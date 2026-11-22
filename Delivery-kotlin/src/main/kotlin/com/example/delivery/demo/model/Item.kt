package com.example.cardapio.demo.model

import jakarta.persistence.*

@Entity
@Table(name = "item")
class Item(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 100)
    var nome: String = "",

    var completed: Boolean = false
)