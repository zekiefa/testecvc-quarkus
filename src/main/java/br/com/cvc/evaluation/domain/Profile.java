package br.com.cvc.evaluation.domain;

/** Papel do usuário (ex.: {@code user}). O nome é o que vai no claim {@code groups} do JWT. */
public record Profile(String name) {

}
