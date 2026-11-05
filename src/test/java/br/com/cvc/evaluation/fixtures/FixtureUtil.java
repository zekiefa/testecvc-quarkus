package br.com.cvc.evaluation.fixtures;

import java.util.Set;
import java.util.UUID;

import br.com.cvc.evaluation.domain.Profile;
import br.com.cvc.evaluation.domain.User;
import org.jeasy.random.EasyRandom;

/** Geradores de valores aleatórios para testes. */
public final class FixtureUtil {
    private static final EasyRandom EASY_RANDOM = new EasyRandom();

    private FixtureUtil() {
    }

    public static String nextString() {
        return EASY_RANDOM.nextObject(String.class);
    }

    public static String nextUUID() {
        return UUID.randomUUID().toString();
    }

    public static Integer nextInt() {
        return EASY_RANDOM.nextInt();
    }

    public static User nextUser() {
        return new User(nextString(), nextUUID(), Set.of(new Profile("user")));
    }
}
