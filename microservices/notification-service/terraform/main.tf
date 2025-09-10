terraform {
  required_providers {
    aws = {
      version = "~> 6.12.0"
      source  = "hashicorp/aws"
    }
  }
  required_version = ">= 1.5.0"
}



# ====================================
# Random ID para evitar colisiones en nombres de buckets
# ====================================
resource "random_id" "bucket_id" {
  byte_length = 4
}

# ====================================
# S3 Bucket para almacenar avatares
# ====================================
resource "aws_s3_bucket" "AvatarBucket" {
  bucket = "inferno-notification-avatars-${random_id.bucket_id.hex}"
}

# ====================================
# IAM Role para Lambda
# ====================================
resource "aws_iam_role" "lambda_role" {
  name = "notification-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Políticas para que Lambda pueda escribir logs, acceder a DynamoDB, SNS y S3
resource "aws_iam_policy" "lambda_policy" {
  name        = "notification-lambda-policy"
  description = "Permite a Lambda escribir logs, leer/escribir DynamoDB, publicar en SNS y acceder a S3"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:PutItem", "dynamodb:GetItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.AvatarBucket.arn}/*"
      }
    ]
  })
}

# Asociar la policy al role
resource "aws_iam_role_policy_attachment" "lambda_policy_attach" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = aws_iam_policy.lambda_policy.arn
}

# ====================================
# NOTIFICATION SERVICE
# ====================================

# SNS Topic para notificaciones
resource "aws_sns_topic" "NotificationTopic" {
  name = "notification-topic"
}

# DynamoDB para guardar notificaciones
resource "aws_dynamodb_table" "NotificationTable" {
  name         = "notification-table"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  attribute {
    name = "id"
    type = "S"
  }

  tags = {
    Environment  = "production"
    Microservice = "notification-service"
  }
}

# ====================================
# Lambda que procesa notificaciones (desde S3)
# ====================================
resource "aws_lambda_function" "NotificationLambda" {
  function_name = "notification-lambda"

  # Aquí usamos el jar que ya subiste a S3
  s3_bucket = "inferno-notification-templates"
  s3_key    = "notification-service.jar"

  handler     = "com.example.notification_service.notification_service.NotificationServiceTestApp::main"
  runtime     = "java17"
  timeout     = 300
  memory_size = 512
  role        = aws_iam_role.lambda_role.arn
}

# Permiso para que SNS invoque la Lambda
resource "aws_lambda_permission" "AllowSNSToInvokeLambda" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.NotificationLambda.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.NotificationTopic.arn
}

# Subscripción SNS -> Lambda
resource "aws_sns_topic_subscription" "SnsToLambda" {
  topic_arn = aws_sns_topic.NotificationTopic.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.NotificationLambda.arn
}

# ====================================
# Outputs
# ====================================
output "NotificationTopicArn" {
  value = aws_sns_topic.NotificationTopic.arn
}

output "NotificationTableName" {
  value = aws_dynamodb_table.NotificationTable.name
}

output "NotificationLambdaName" {
  value = aws_lambda_function.NotificationLambda.function_name
}

output "AvatarBucketName" {
  value = aws_s3_bucket.AvatarBucket.bucket
}
