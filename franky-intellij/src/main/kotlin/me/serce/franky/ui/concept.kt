package me.serce.franky.ui

import javax.swing.JComponent

interface HasComponent {
    fun createComponent(): JComponent
}

interface ViewModel : HasComponent
interface View : HasComponent

