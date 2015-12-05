package mesosphere.marathon.api.v2

import com.wix.accord._

import play.api.libs.json._

object Validation {

  implicit def optional[T](implicit validator: Validator[T]): Validator[Option[T]] = {
    new Validator[Option[T]] {
      override def apply(option: Option[T]): Result = option.map(validator).getOrElse(Success)
    }
  }

  implicit def every[T](implicit validator: Validator[T]): Validator[Iterable[T]] = {
    new Validator[Iterable[T]] {
      override def apply(seq: Iterable[T]): Result = {

        val violations = seq.map(item => (item, validator(item))).zipWithIndex.collect {
          case ((item, f: Failure), pos: Int) => GroupViolation(item, "not valid", Some(s"[$pos]"), f.violations)
        }

        if(violations.isEmpty) Success
        else Failure(Set(GroupViolation(seq, "seq contains elements, which are not valid", None, violations.toSet)))
      }
    }
  }

  implicit lazy val failureWrites: Writes[Failure] = Writes { f =>
    // TODO AW: get rid of toSeq
    Json.obj("errors" -> {f.violations.size match {
      case 1 => violationToJsValue(f.violations.head)
      case _ => JsArray(f.violations.toSeq.map(violationToJsValue(_)))
    }})
  }

  implicit lazy val ruleViolationWrites: Writes[RuleViolation] = Writes { v =>
      Json.obj(
        "attribute" -> v.description,
        "error" -> v.constraint
      )
  }

  implicit lazy val groupViolationWrites: Writes[GroupViolation] = Writes { v =>
    // TODO AW: get rid of toSeq
    v.value match {
      case Some(s) =>
        violationToJsValue(v.children.head, v.description)
      case _ => v.children.size match {
        case 1 => violationToJsValue(v.children.head, v.description)
        case _ => JsArray(v.children.toSeq.map(c =>
          violationToJsValue(c, v.description, parentSeq = true)
        ))
      }
    }
  }

  private def concatPath(parent: String, child: Option[String], parentSeq: Boolean): String = {
    child.map(c => parent + { if(parentSeq) "" else "." } + c).getOrElse(parent)
  }

  private def violationToJsValue(violation: Violation,
                                 parentDesc: Option[String] = None,
                                 parentSeq: Boolean = false): JsValue = {
    violation match {
      case r: RuleViolation => Json.toJson(parentDesc.map(p =>
        r.withDescription(concatPath(p, r.description, parentSeq)))
        .getOrElse(r))
      case g: GroupViolation => Json.toJson(parentDesc.map(p =>
        g.withDescription(concatPath(p, g.description, parentSeq)))
        .getOrElse(g))
    }
  }
}
