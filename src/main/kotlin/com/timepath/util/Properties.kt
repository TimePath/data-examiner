package com.timepath.util

import java.beans.PropertyChangeSupport
import java.beans.PropertyVetoException
import java.beans.VetoableChangeSupport
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty

public class BeanProperty<T>(initialValue: T,
                             val pcs: PropertyChangeSupport,
                             val vcs: VetoableChangeSupport) : ReadWriteProperty<Any?, T> {
    private var value = initialValue

    public override fun get(thisRef: Any?, desc: PropertyMetadata) = value

    public override fun set(thisRef: Any?, desc: PropertyMetadata, value: T) {
        val old = this.value
        if (old == value) return
        vcs.fireVetoableChange(desc.name, old, value)
        this.value = value
        pcs.firePropertyChange(desc.name, old, value)
    }
}

public inline fun <R> KMutableProperty<R>.observe(pcs: PropertyChangeSupport,
                                                  [inlineOptions(InlineOption.ONLY_LOCAL_RETURN)]
                                                  function: (old: R, new: R) -> Unit) {
    pcs.addPropertyChangeListener(this.name) {
        [suppress("UNCHECKED_CAST")]
        function(it.getOldValue() as R, it.getNewValue() as R)
    }
}

public inline fun <R> KMutableProperty<R>.observe(vcs: VetoableChangeSupport,
                                                  [inlineOptions(InlineOption.ONLY_LOCAL_RETURN)]
                                                  function: (old: R, new: R) -> String?) {
    vcs.addVetoableChangeListener(this.name) {
        [suppress("UNCHECKED_CAST")]
        function(it.getOldValue() as R, it.getNewValue() as R)?.let { e ->
            throw PropertyVetoException("${this.name}: ${e}", it)
        }
    }
}
