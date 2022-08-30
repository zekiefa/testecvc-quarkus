package br.com.cvc.evaluation.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Token {
    private String type;
    private String token;
}
