package com.oriooneee.jet.navigation.utils

fun <T> List<T>.containsAny(
    vararg elements: T
): Boolean {
    for (element in elements) {
        if (this.contains(element)) {
            return true
        }
    }
    return false
}