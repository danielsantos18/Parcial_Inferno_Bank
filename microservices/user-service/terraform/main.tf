terraform {
  required_providers {
    aws = {
      version = "~> 6.12.0"
      source  = "hashicorp/aws"
    }
  }
}

#===============================================LAMBDAS===================================================
#- register-user-lambda
#- login-user-lambda
#- update-user-lambda
#- upload-avatar-user-lambda
#- get-profile-user-lambda

resource "null_resource" "lambda_build_trigger" {
  triggers = {
    build_number = timestamp()
  }
}

resource "aws_lambda_function" "RegisterUserLmb" {
  filename         = "../target/user-service-lambda-jar-with-dependencies.jar"
  function_name    = var.register_user_lambda_name
  handler          = "com.inferno.user_service.handler.UserRegisterLambda::handleRequest"
  runtime          = "java17"
  timeout          = 900
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/user-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"
}

resource "aws_lambda_function" "LoginUserLmb" {
  filename         = var.lambda_user_filename
  function_name    = "login-user-lambda"
  handler          = "com.inferno.user_service.handler.LoginUserLambda::handleRequest"
  runtime          = "java17"
  timeout          = 900
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/user-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"
}

resource "aws_lambda_function" "UpdateUserLmb" {
  filename         = var.lambda_user_filename
  function_name    = "update-user-lambda"
  handler          = "com.inferno.user_service.handler.UpdateUserLambda::handleRequest"
  runtime          = "java17"
  timeout          = 900
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/user-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"
}

resource "aws_lambda_function" "UploadUserPhotoLmb" {
  filename         = var.lambda_user_filename
  function_name    = "upload-avatar-user-lambda"
  handler          = "com.inferno.user_service.handler.UploadAvatarLambda::handleRequest"
  runtime          = "java17"
  timeout          = 900
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/user-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"

  environment {
    variables = {
      infernoavatarimagebucket = aws_s3_bucket.AvatarBucket.bucket
    }
  }
}

resource "aws_lambda_function" "GetUserProfileLmb" {
  filename         = var.lambda_user_filename
  function_name    = "get-profile-user-lambda"
  handler          = "com.inferno.user_service.handler.GetUserProfileLambda::handleRequest"
  runtime          = "java17"
  timeout          = 900
  memory_size      = 256
  role             = aws_iam_role.lambda_role.arn
  source_code_hash = "${filebase64sha256("../target/user-service-lambda-jar-with-dependencies.jar")}-${null_resource.lambda_build_trigger.id}"
}

#==============================================ROLES AND POLICY===========================================

resource "aws_iam_role_policy" "iam_policy_for_lambda" {
  name   = "lambdaUserRegister"
  policy = data.aws_iam_policy_document.lambda_execution.json
  role   = aws_iam_role.lambda_role.id
}

resource "aws_iam_policy" "lambda_secrets_policy" {
  name = "lambda-secrets-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.jwt_secret.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_attach" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_secrets_policy.arn
}

resource "aws_iam_role" "lambda_role" {
  name               = "ExecutionLamba"
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

#===============================================API GATEWAYS=================================================

#==========================REGISTER============================
resource "aws_api_gateway_rest_api" "UserRegisterApi" {
  name        = "user api for register"
  description = "esta api gateway sirve para registrar un usuario"
}

//Resource Api Gateway
resource "aws_api_gateway_resource" "CreateUser" {
  rest_api_id = aws_api_gateway_rest_api.UserRegisterApi.id
  parent_id   = aws_api_gateway_rest_api.UserRegisterApi.root_resource_id
  path_part   = "register"
}

//Method Del Api Gateway
resource "aws_api_gateway_method" "PostCreateUser" {
  resource_id   = aws_api_gateway_resource.CreateUser.id
  rest_api_id   = aws_api_gateway_rest_api.UserRegisterApi.id
  http_method   = "POST"
  authorization = "NONE"
}

//Connect Del Api A Lambda
resource "aws_api_gateway_integration" "IntegrationPostCreateUser" {
  rest_api_id             = aws_api_gateway_rest_api.UserRegisterApi.id
  resource_id             = aws_api_gateway_resource.CreateUser.id
  http_method = aws_api_gateway_method.PostCreateUser.http_method
  //consultar metodos de integracion entre api y lambda
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.RegisterUserLmb.invoke_arn
}

//Connect De La Lambda A Api
resource "aws_lambda_permission" "ApiGwLambdaRegisterUser" {
  statement_id  = "AllowExcutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = var.register_user_lambda_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.UserRegisterApi.execution_arn}/*/POST/${aws_api_gateway_resource.CreateUser.path_part}"
}

//Deploy De La Api
resource "aws_api_gateway_deployment" "deploymentRegisterEndpoint" {
  rest_api_id = aws_api_gateway_rest_api.UserRegisterApi.id
  depends_on = [aws_api_gateway_integration.IntegrationPostCreateUser, aws_lambda_permission.ApiGwLambdaRegisterUser]

}

//stage -> dev,qa,pre-production
resource "aws_api_gateway_stage" "Stage" {
  deployment_id = aws_api_gateway_deployment.deploymentRegisterEndpoint.id
  rest_api_id   = aws_api_gateway_rest_api.UserRegisterApi.id
  stage_name    = var.stage
}

output "apiUrlRegister" {
  value = "${aws_api_gateway_stage.Stage.invoke_url}/${aws_api_gateway_resource.CreateUser.path_part}"
}

#==========================UPDATE============================
resource "aws_api_gateway_rest_api" "UserUpdateApi" {
  name        = "user api for update"
  description = "esta api gateway sirve para actualizar un usuario"
}

//Resource Api Gateway
resource "aws_api_gateway_resource" "UpdateUserResource" {
  rest_api_id = aws_api_gateway_rest_api.UserUpdateApi.id
  parent_id   = aws_api_gateway_rest_api.UserUpdateApi.root_resource_id
  path_part   = "profile"
}

// Resource para el path variable {userId}
resource "aws_api_gateway_resource" "UserIdResource" {
  rest_api_id = aws_api_gateway_rest_api.UserUpdateApi.id
  parent_id   = aws_api_gateway_resource.UpdateUserResource.id
  path_part   = "{uuid}"
}

//Method Del Api Gateway
resource "aws_api_gateway_method" "PutUpdateUser" {
  resource_id = aws_api_gateway_resource.UserIdResource.id //Debe Apuntar Al Recurso Donde Define El Parameter uuid
  rest_api_id = aws_api_gateway_rest_api.UserUpdateApi.id
  http_method = "PUT"
  authorization = "NONE"

  // Configurar request parameters para path variables
  request_parameters = {
    "method.request.path.uuid" = true
  }
}

//Connect Del Api A Lambda
resource "aws_api_gateway_integration" "IntegrationPutUpdateUser" {
  rest_api_id = aws_api_gateway_rest_api.UserUpdateApi.id
  resource_id = aws_api_gateway_resource.UserIdResource.id //Debe Apuntar Al Recurso Donde Define El Parameter uuid
  http_method = aws_api_gateway_method.PutUpdateUser.http_method
  //consultar metodos de integracion entre api y lambda
  integration_http_method = "POST"  // Lambda siempre usa POST
  type        = "AWS_PROXY"
  uri         = aws_lambda_function.UpdateUserLmb.invoke_arn
}

//Connect De La Lambda A Api
resource "aws_lambda_permission" "ApiGwLambdaUpdateUser" {
  statement_id  = "AllowExcutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = "update-user-lambda"
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.UserUpdateApi.execution_arn}/*/PUT/${aws_api_gateway_resource.UpdateUserResource.path_part}/*"
}

//Deploy De La Api
resource "aws_api_gateway_deployment" "deploymentUpdateEndpoint" {
  rest_api_id = aws_api_gateway_rest_api.UserUpdateApi.id
  depends_on = [aws_api_gateway_integration.IntegrationPutUpdateUser, aws_lambda_permission.ApiGwLambdaUpdateUser]
}

//stage -> dev,qa,pre-production
resource "aws_api_gateway_stage" "StageUpdate" {
  deployment_id = aws_api_gateway_deployment.deploymentUpdateEndpoint.id
  rest_api_id   = aws_api_gateway_rest_api.UserUpdateApi.id
  stage_name    = var.stage
}

output "apiUrlUpdate" {
  value = "${aws_api_gateway_stage.StageUpdate.invoke_url}/${aws_api_gateway_resource.UpdateUserResource.path_part}/${aws_api_gateway_resource.UserIdResource.path_part}"
}

#==========================GET===========================
resource "aws_api_gateway_rest_api" "UserGetApi" {
  name        = "user api for getid"
  description = "esta api gateway sirve para obtener un usuario por id"
}

//Resource Api Gateway
resource "aws_api_gateway_resource" "GetUserResource" {
  rest_api_id = aws_api_gateway_rest_api.UserGetApi.id
  parent_id   = aws_api_gateway_rest_api.UserGetApi.root_resource_id
  path_part   = "profile"
}

// Resource para el path variable {userId}
resource "aws_api_gateway_resource" "UserGetIdResource" {
  rest_api_id = aws_api_gateway_rest_api.UserGetApi.id
  parent_id   = aws_api_gateway_resource.GetUserResource.id
  path_part   = "{uuid}"
}

//Method Del Api Gateway
resource "aws_api_gateway_method" "MethodGetUser" {
  resource_id = aws_api_gateway_resource.UserGetIdResource.id //Debe Apuntar Al Recurso Donde Define El Parameter uuid
  rest_api_id = aws_api_gateway_rest_api.UserGetApi.id
  http_method = "GET"
  authorization = "NONE"

  // Configurar request parameters para path variables
  request_parameters = {
    "method.request.path.uuid" = true
  }
}

//Connect Del Api A Lambda
resource "aws_api_gateway_integration" "IntegrationGetUser" {
  rest_api_id = aws_api_gateway_rest_api.UserGetApi.id
  resource_id = aws_api_gateway_resource.UserGetIdResource.id //Debe Apuntar Al Recurso Donde Define El Parameter uuid
  http_method = aws_api_gateway_method.MethodGetUser.http_method
  //consultar metodos de integracion entre api y lambda
  integration_http_method = "POST"  // Lambda siempre usa POST
  type        = "AWS_PROXY"
  uri         = aws_lambda_function.GetUserProfileLmb.invoke_arn
}

//Connect De La Lambda A Api
resource "aws_lambda_permission" "ApiGwLambdaGetUser" {
  statement_id  = "AllowExcutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = "get-profile-user-lambda"
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.UserGetApi.execution_arn}/*/GET/${aws_api_gateway_resource.GetUserResource.path_part}/*"
}

//Deploy De La Api
resource "aws_api_gateway_deployment" "deploymentGetEndpoint" {
  rest_api_id = aws_api_gateway_rest_api.UserGetApi.id
  depends_on = [aws_api_gateway_integration.IntegrationGetUser, aws_lambda_permission.ApiGwLambdaGetUser]
}

//stage -> dev,qa,pre-production
resource "aws_api_gateway_stage" "StageGet" {
  deployment_id = aws_api_gateway_deployment.deploymentGetEndpoint.id
  rest_api_id   = aws_api_gateway_rest_api.UserGetApi.id
  stage_name    = var.stage
}

output "apiUrlGet" {
  value = "${aws_api_gateway_stage.StageGet.invoke_url}/${aws_api_gateway_resource.GetUserResource.path_part}/${aws_api_gateway_resource.UserGetIdResource.path_part}"
}

#==========================UPLOAD===========================
resource "aws_api_gateway_rest_api" "UserUploadApi" {
  name        = "user api for Upload Avatar"
  description = "esta api gateway sirve para subir un avatar de un usuario por id"
}

//Resource Api Gateway
resource "aws_api_gateway_resource" "UploadUserResource" {
  rest_api_id = aws_api_gateway_rest_api.UserUploadApi.id
  parent_id   = aws_api_gateway_rest_api.UserUploadApi.root_resource_id
  path_part   = "profile"
}

// Resource para el path variable {userId}
resource "aws_api_gateway_resource" "UserUploadIdResource" {
  rest_api_id = aws_api_gateway_rest_api.UserUploadApi.id
  parent_id   = aws_api_gateway_resource.UploadUserResource.id
  path_part   = "{uuid}"
}

# /profile/{user_id}/avatar
resource "aws_api_gateway_resource" "UserUploadAvatarResource" {
  rest_api_id = aws_api_gateway_rest_api.UserUploadApi.id
  parent_id   = aws_api_gateway_resource.UserUploadIdResource.id
  path_part   = "avatar"
}

//Method Del Api Gateway
resource "aws_api_gateway_method" "PostUploadUser" {
  resource_id = aws_api_gateway_resource.UserUploadAvatarResource.id
  //Debe Apuntar Al Recurso Donde Define El Parameter uuid
  rest_api_id = aws_api_gateway_rest_api.UserUploadApi.id
  http_method = "POST"
  authorization = "NONE"

  // Configurar request parameters para path variables
  request_parameters = {
    "method.request.path.uuid" = true
  }
}

//Connect Del Api A Lambda
resource "aws_api_gateway_integration" "IntegrationPostUploadUser" {
  rest_api_id = aws_api_gateway_rest_api.UserUploadApi.id
  resource_id = aws_api_gateway_resource.UserUploadAvatarResource.id
  //Debe Apuntar Al Recurso Donde Define El Parameter uuid
  http_method = aws_api_gateway_method.PostUploadUser.http_method
  //consultar metodos de integracion entre api y lambda
  integration_http_method = "POST"  // Lambda siempre usa POST
  type        = "AWS_PROXY"
  uri         = aws_lambda_function.UploadUserPhotoLmb.invoke_arn
}

//Connect De La Lambda A Api
resource "aws_lambda_permission" "ApiGwLambdaUploadUser" {
  statement_id  = "AllowExcutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = "upload-avatar-user-lambda"
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.UserUploadApi.execution_arn}/*/POST/${aws_api_gateway_resource.UploadUserResource.path_part}/*"
}

//Deploy De La Api
resource "aws_api_gateway_deployment" "deploymentPostUpload" {
  rest_api_id = aws_api_gateway_rest_api.UserUploadApi.id
  depends_on = [aws_api_gateway_integration.IntegrationPostUploadUser, aws_lambda_permission.ApiGwLambdaUploadUser]
}

//stage -> dev,qa,pre-production
resource "aws_api_gateway_stage" "StageUpload" {
  deployment_id = aws_api_gateway_deployment.deploymentPostUpload.id
  rest_api_id   = aws_api_gateway_rest_api.UserUploadApi.id
  stage_name    = var.stage
}

output "apiUrlUpload" {
  value = "${aws_api_gateway_stage.StageUpload.invoke_url}/${aws_api_gateway_resource.UploadUserResource.path_part}/${aws_api_gateway_resource.UserUploadIdResource.path_part}/${aws_api_gateway_resource.UserUploadAvatarResource.path_part}"
}

#==========================LOGIN============================
resource "aws_api_gateway_rest_api" "UserLoginApi" {
  name        = "user api for login"
  description = "esta api gateway sirve para logear un usuario"
}

//Resource Api Gateway
resource "aws_api_gateway_resource" "LoginUserResource" {
  rest_api_id = aws_api_gateway_rest_api.UserLoginApi.id
  parent_id   = aws_api_gateway_rest_api.UserLoginApi.root_resource_id
  path_part   = "login"
}

//Method Del Api Gateway
resource "aws_api_gateway_method" "PostLoginUser" {
  resource_id   = aws_api_gateway_resource.LoginUserResource.id
  rest_api_id   = aws_api_gateway_rest_api.UserLoginApi.id
  http_method   = "POST"
  authorization = "NONE"
}

//Connect Del Api A Lambda
resource "aws_api_gateway_integration" "IntegrationPostLoginUser" {
  rest_api_id             = aws_api_gateway_rest_api.UserLoginApi.id
  resource_id             = aws_api_gateway_resource.LoginUserResource.id
  http_method = aws_api_gateway_method.PostLoginUser.http_method
  //consultar metodos de integracion entre api y lambda
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.LoginUserLmb.invoke_arn
}

//Connect De La Lambda A Api
resource "aws_lambda_permission" "ApiGwLambdaLoginUser" {
  statement_id  = "AllowExcutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = "login-user-lambda"
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.UserLoginApi.execution_arn}/*/POST/${aws_api_gateway_resource.LoginUserResource.path_part}"
}

//Deploy De La Api
resource "aws_api_gateway_deployment" "deploymentLoginEndpoint" {
  rest_api_id = aws_api_gateway_rest_api.UserLoginApi.id
  depends_on = [aws_api_gateway_integration.IntegrationPostLoginUser, aws_lambda_permission.ApiGwLambdaLoginUser]

}

//stage -> dev,qa,pre-production
resource "aws_api_gateway_stage" "StageLogin" {
  deployment_id = aws_api_gateway_deployment.deploymentLoginEndpoint.id
  rest_api_id   = aws_api_gateway_rest_api.UserLoginApi.id
  stage_name    = var.stage
}

output "apiUrlLogin" {
  value = "${aws_api_gateway_stage.StageLogin.invoke_url}/${aws_api_gateway_resource.LoginUserResource.path_part}"
}


#===============================================S3=================================================
resource "aws_s3_bucket" "AvatarBucket" {
  bucket = "infernoavatarimagebucket"
}

resource "aws_iam_policy" "s3WriteAccess" {
  name        = "S3WriteAccessToBucket"
  description = "policy for write access"
  policy      = data.aws_iam_policy_document.s3_policy.json
}

#===============================================SECRET=================================================
resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "jwtSecret"
}

resource "aws_secretsmanager_secret_version" "jwt_secret_version" {
  secret_id = aws_secretsmanager_secret.jwt_secret.id
  secret_string = jsonencode({
    JWT_SECRET = "aEIu9S7cvZUnPJWezau3rKUCxj4BLtpCVzhVSyam93prJOxofs7688P0OD5tmTIsLL6u7G9HpXvT"
  })
}


#===============================================DYNAMO DB=================================================

resource "aws_dynamodb_table" "user_table" {
  name           = "user-table"
  billing_mode   = "PROVISIONED"
  read_capacity  = var.reading_capacity_user_table
  write_capacity = var.writing_capacity_user_table
  hash_key = "uuid"  # Partition key
  range_key      = "documentNumber"

  attribute {
    name = "uuid"
    type = "S"
  }

  attribute {
    name = "email"
    type = "S"
  }

  attribute {
    name = "documentNumber"
    type = "S"
  }

  # Índice global secundario para búsquedas por documentNumber
  global_secondary_index {
    name            = "DocumentNumberIndex"
    hash_key        = "documentNumber"
    write_capacity  = var.writing_capacity_user_table
    read_capacity   = var.reading_capacity_user_table
    projection_type = "ALL"
  }

  # Índice global secundario para búsquedas por email
  global_secondary_index {
    name            = "EmailIndex"
    hash_key        = "email"
    write_capacity  = var.writing_capacity_user_table
    read_capacity   = var.reading_capacity_user_table
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = false
  }

  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Environment  = "production"
    Microservice = "user-service"
  }

}


