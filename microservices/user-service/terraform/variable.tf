variable "register_user_lambda_name" {
  type        = string
  default     = "register-user-lambda"
  description = "Variable para nombre de lambda de registro"
}

variable "register_user_lambda_file_name" {
  default = "register-user-lmb.zip"
}

variable "lambda_user_filename" {
  default = "../target/user-service-lambda-jar-with-dependencies.jar"
}

variable "reading_capacity_user_table" {
  type    = number
  default = 20
}

variable "writing_capacity_user_table" {
  type    = number
  default = 20
}

variable "stage" {
  type    = string
  default = "users"
}