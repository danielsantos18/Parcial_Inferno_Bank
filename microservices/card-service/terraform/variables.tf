variable "project" {
  type    = string
  default = "inferno-bank"
}

variable "env" {
  type    = string
  default = "dev"
}

variable "owner" {
  type    = string
  default = "team-card"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

# Prefijo común para nombrar recursos
variable "name_prefix" {
  type        = string
  description = "Prefijo para nombres de recursos"
  default     = "inferno-bank-dev"
}

# Bucket para reportes de transacciones
variable "transactions_report_bucket_name" {
  type        = string
  description = "Nombre único global para S3"
  default     = "inferno-bank-dev-transactions-report-123456789"
}

# Bucket S3 donde se suben los artefactos (JARs) de las Lambdas
variable "lambda_code_s3_bucket" {
  type        = string
  description = "Nombre del bucket S3 que contiene los JARs de las funciones Lambda"
  default     = "inferno-bank-dev-card-lambda-bucket"
}

# Versión del objeto en S3 (usa null si no tienes versionado)
variable "lambda_code_version" {
  type        = string
  description = "Versión de los objetos JAR en S3"
  default     = null
}

# Claves JAR por cada Lambda (ajusta según tu build)
variable "lambda_jars" {
  type = object({
    create_request_card  = string
    card_activate        = string
    card_purchase        = string
    card_transaction     = string
    card_paid_credit     = string
    card_get_report      = string
    card_approval_worker = string
    card_request_failed  = string
  })

  default = {
    create_request_card  = "card/create-request-card-lambda.jar"
    card_activate        = "card/card-activate-lambda.jar"
    card_purchase        = "card/card-purchase-lambda.jar"
    card_transaction     = "card/card-transaction-save-lambda.jar"
    card_paid_credit     = "card/card-paid-credit-card-lambda.jar"
    card_get_report      = "card/card-get-report-lambda.jar"
    card_approval_worker = "card/card-approval-worker.jar"
    card_request_failed  = "card/card-request-failed.jar"
  }
}
