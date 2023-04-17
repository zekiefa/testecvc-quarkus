package br.com.cvc.evaluation.domain;

import java.util.Set;

public record User(String username, String password, Set<Profile> profiles) {

}
