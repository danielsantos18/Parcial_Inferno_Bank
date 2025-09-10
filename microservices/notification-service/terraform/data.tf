# ====================================
# Empaquetar el JAR del user-service para Lambda
# ====================================
data "archive_file" "lambda_user_create_file" {
  type        = "zip"
  source_file = "../../user-service/target/user-service-lambda-jar-with-dependencies.jar"
  output_path = "../../user-service/target/user-service-lambda.zip"
}


# ====================================
# IAM Policies
# ====================================
data "aws_iam_policy_document" "assume_role" {
  statement {
    effect = "Allow"
    actions = [
      "sts:AssumeRole"
    ]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "lambda_execution" {
  statement {
    effect = "Allow"
    actions = [
      "dynamodb:GetItem"
    ]
    resources = [
      "*"
    ]
  }
}

data "aws_iam_policy_document" "s3_policy" {
  statement {
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject"
    ]
    resources = ["${aws_s3_bucket.AvatarBucket.arn}/*"]
  }
}
