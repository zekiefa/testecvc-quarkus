package br.com.cvc.evaluation.domain;

import java.util.List;

public record Hotel(Integer id, String cityName, String name, List<Room> rooms) {

}
