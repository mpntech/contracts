package com.tradeix.contractcomposition.contracts.common

/**
 * An interface for identifying objects whose only responsibility is to retrieve the relevant field from some object
 * because the calling function doesn't know what fields exist on the object.
 * For example, some contract might require that the amount on some state A is less than the amount on some state B.
 * Instead of needing to know that both states will have an amount at compile time, they can use this object which will
 * retrieve those amounts from those states only if they exist.
 *
 * The only function defined in this interface takes some object which the calling function expects or requires can be
 * cast as some type as well as an identifier for a field on that object. This function should cast the given object and
 * call the getter method for the identified field. We don't mandate how that indentifier needs to look - could be a
 * string or a lighter mechanism to identify some field (e.g. an int which this function will map to some field).
 */
interface FieldRetriever {
    fun retrieveField(objToCast: Any, fieldIdentifier: Any): Any
}