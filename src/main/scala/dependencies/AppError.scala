package dependencies

sealed trait AppError extends Throwable

object AppError {
  case object MissingBuildSbt extends AppError
}
