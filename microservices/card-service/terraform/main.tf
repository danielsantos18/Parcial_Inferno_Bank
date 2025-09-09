terraform {
  required_providers {
    aws = {
      version = "~> 6.12.0"
      source  = "hashicorp/aws"
    }
  }
}

//===============================SQS Y DLQ===================================
resource "aws_sqs_queue" "card_dlq" {
  name = "error-create-request-card-sqs"
}

resource "aws_sqs_queue" "create_request_queue" {
  name = "create-request-card-sqs"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.card_dlq.arn
    maxReceiveCount     = 5
  })
  visibility_timeout_seconds = 100
}

//===============================DYNAMODB===================================
resource "aws_dynamodb_table" "card_table" {
  name           = "card-table"
  billing_mode   = "PROVISIONED"
  read_capacity  = 20
  write_capacity = 20
  hash_key       = "uuid"
  range_key      = "createdAt"

  attribute {
    name = "uuid"
    type = "S"
  }
  attribute {
    name = "createdAt"
    type = "S"
  }
}

resource "aws_dynamodb_table" "transaction_table" {
  name           = "transaction-table"
  billing_mode   = "PROVISIONED"
  read_capacity  = 20
  write_capacity = 20
  hash_key       = "uuid"
  range_key      = "createdAt"

  attribute {
    name = "uuid"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }
}

resource "aws_dynamodb_table" "card_table_error" {
  name           = "card-table-error"
  billing_mode   = "PROVISIONED"
  read_capacity  = 20
  write_capacity = 20
  hash_key       = "uuid"
  range_key      = "createdAt"

  attribute {
    name = "uuid"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  }
}

//===============================S3===================================
resource "aws_s3_bucket" "transactions_reports" {
  bucket = "inferno-bank-transactions-report-bucket"
}

resource "aws_iam_policy" "s3WriteAccess" {
  name        = "S3WriteAccessToCardBucket"
  description = "policy for write access to carbucket"
  policy      = data.aws_iam_policy_document.s3_policy.json
}

//===============================ROLES AND POLICY===================================

resource "null_resource" "lambda_build_trigger" {
  triggers = {
    build_number = timestamp()
  }
}

resource "aws_iam_role" "lambda_role" {
  name               = "ExecutionLambaCard"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

resource "aws_iam_role_policy_attachment" "lmb_policy_execution" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "dynamo_access" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess"
}

resource "aws_iam_role_policy_attachment" "s3_access" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
}

resource "aws_iam_role_policy" "sqs_access" {
  name = "SQSAccessPolicy"
  role = aws_iam_role.lambda_role.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility"
        ]
        Resource = aws_sqs_queue.create_request_queue.arn
      }
    ]
  })
}

# Event Source Mapping para conectar SQS con Lambda
resource "aws_lambda_event_source_mapping" "sqs_to_approval_worker" {
  event_source_arn = aws_sqs_queue.create_request_queue.arn
  function_name    = aws_lambda_function.card_approval_worker.arn
  batch_size       = 1
  enabled          = true
}

resource "aws_lambda_permission" "allow_sqs" {
  statement_id  = "AllowExecutionFromSQS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.CreateCardLmb.function_name
  principal     = "sqs.amazonaws.com"
  source_arn    = aws_sqs_queue.create_request_queue.arn
}

//===============================LAMBDAS===================================

resource "aws_lambda_function" "CreateCardLmb" {
  filename         = "../target/card-service-lambda-jar-with-dependencies.jar"
  function_name    = "create-card-request-lambda"
  handler          = "com.inferno.card_service.handler.CreateCardLambda::handleRequest"
  runtime          = "java17"
  timeout          = 90
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/card-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"

  environment {
    variables = {
      SQS_QUEUE_URL = aws_sqs_queue.create_request_queue.id
    }
  }
}

# Lambda que consume SQS
resource "aws_lambda_function" "card_approval_worker" {
  filename         = "../target/card-service-lambda-jar-with-dependencies.jar"
  function_name    = "card-approval-worker"
  handler          = "com.inferno.card_service.handler.CardApprovalWorker::handleRequest"
  runtime          = "java17"
  timeout          = 90
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/card-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"

  environment {
    variables = {
      USER_SERVICE_URL = "https://imjygkm24m.execute-api.us-east-2.amazonaws.com"
    }
  }
}

resource "aws_lambda_function" "card_activate_lambda" {
  filename         = "../target/card-service-lambda-jar-with-dependencies.jar"
  function_name    = "card-activate-lambda"
  handler          = "com.inferno.card_service.handler.CardActivateLambda::handleRequest"
  runtime          = "java17"
  timeout          = 90
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/card-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"

  environment {
    variables = {
      DYNAMODB_TABLE = "card-table"
    }
  }
}


//===============================API GATEWAY===================================

#==========================API GATEWAY PARA CREAR TARJETAS===========================
resource "aws_api_gateway_rest_api" "CardCreateApi" {
  name        = "card-create-api"
  description = "API para creación de tarjetas (crédito/débito)"
}

#=======================RECURSOS Y MÉTODOS=======================

# Recurso principal para cards
resource "aws_api_gateway_resource" "CardsResource" {
  rest_api_id = aws_api_gateway_rest_api.CardCreateApi.id
  parent_id   = aws_api_gateway_rest_api.CardCreateApi.root_resource_id
  path_part   = "cards"
}

# Recurso para /cards/request
resource "aws_api_gateway_resource" "CardRequestResource" {
  rest_api_id = aws_api_gateway_rest_api.CardCreateApi.id
  parent_id   = aws_api_gateway_resource.CardsResource.id
  path_part   = "request"
}

#=======================MÉTODO HTTP=======================

# POST /cards/request
resource "aws_api_gateway_method" "CardRequestMethod" {
  rest_api_id   = aws_api_gateway_rest_api.CardCreateApi.id
  resource_id   = aws_api_gateway_resource.CardRequestResource.id
  http_method   = "POST"
  authorization = "NONE"
}

#=======================INTEGRACIÓN CON LAMBDA=======================

# Integración para create-card-request-lambda
resource "aws_api_gateway_integration" "CardRequestIntegration" {
  rest_api_id             = aws_api_gateway_rest_api.CardCreateApi.id
  resource_id             = aws_api_gateway_resource.CardRequestResource.id
  http_method             = aws_api_gateway_method.CardRequestMethod.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.CreateCardLmb.invoke_arn
}

#=======================PERMISOS LAMBDA=======================

resource "aws_lambda_permission" "ApiGwCardCreate" {
  statement_id  = "AllowExecutionFromAPIGatewayCardCreate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.CreateCardLmb.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.CardCreateApi.execution_arn}/*/POST/cards/request"
}

#=======================DEPLOYMENT & STAGE=======================

resource "aws_api_gateway_deployment" "CardCreateApiDeployment" {
  rest_api_id = aws_api_gateway_rest_api.CardCreateApi.id
  depends_on = [aws_api_gateway_integration.CardRequestIntegration]
}

resource "aws_api_gateway_stage" "CardCreateApiStage" {
  deployment_id = aws_api_gateway_deployment.CardCreateApiDeployment.id
  rest_api_id   = aws_api_gateway_rest_api.CardCreateApi.id
  stage_name    = "prod"
}

#=======================OUTPUTS=======================

output "card_create_endpoint" {
  value = "${aws_api_gateway_stage.CardCreateApiStage.invoke_url}/cards/request"
}

#==========================API GATEWAY PARA ACTIVAR TARJETAS===========================
resource "aws_api_gateway_rest_api" "CardActivateApi" {
  name        = "card-activate-api"
  description = "API para activación de tarjetas después de 10 transacciones"
}

#=======================RECURSOS Y MÉTODOS=======================

# Recurso principal para card
resource "aws_api_gateway_resource" "CardResource" {
  rest_api_id = aws_api_gateway_rest_api.CardActivateApi.id
  parent_id   = aws_api_gateway_rest_api.CardActivateApi.root_resource_id
  path_part   = "card"
}

# Recurso para /card/activate
resource "aws_api_gateway_resource" "CardActivateResource" {
  rest_api_id = aws_api_gateway_rest_api.CardActivateApi.id
  parent_id   = aws_api_gateway_resource.CardResource.id
  path_part   = "activate"
}

# Método POST para /card/activate
resource "aws_api_gateway_method" "CardActivateMethod" {
  rest_api_id   = aws_api_gateway_rest_api.CardActivateApi.id
  resource_id   = aws_api_gateway_resource.CardActivateResource.id
  http_method   = "POST"
  authorization = "NONE"
}

#=======================INTEGRACIÓN CON LAMBDA=======================

resource "aws_api_gateway_integration" "CardActivateIntegration" {
  rest_api_id             = aws_api_gateway_rest_api.CardActivateApi.id
  resource_id             = aws_api_gateway_resource.CardActivateResource.id
  http_method             = aws_api_gateway_method.CardActivateMethod.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.card_activate_lambda.invoke_arn
}

#=======================PERMISOS LAMBDA=======================

resource "aws_lambda_permission" "ApiGwCardActivate" {
  statement_id  = "AllowExecutionFromAPIGatewayCardActivate"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.card_activate_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.CardActivateApi.execution_arn}/*/POST/card/activate"
}

#=======================DEPLOYMENT & STAGE=======================

resource "aws_api_gateway_deployment" "CardActivateApiDeployment" {
  rest_api_id = aws_api_gateway_rest_api.CardActivateApi.id
  depends_on = [aws_api_gateway_integration.CardActivateIntegration]
}

resource "aws_api_gateway_stage" "CardActivateApiStage" {
  deployment_id = aws_api_gateway_deployment.CardActivateApiDeployment.id
  rest_api_id   = aws_api_gateway_rest_api.CardActivateApi.id
  stage_name    = "prod"
}

#=======================OUTPUTS=======================

output "card_activate_endpoint" {
  value = "${aws_api_gateway_stage.CardActivateApiStage.invoke_url}/card/activate"
}
