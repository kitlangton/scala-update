package update.model

final case class WithVersions[A](value: A, versions: List[Version])
