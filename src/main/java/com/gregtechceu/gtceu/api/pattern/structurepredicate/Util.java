package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import java.util.*;
import java.util.function.Function;

class Util {

    /// Convert a `Codec<A>` to `Codec<List<A>>`, where it accepts both single A value or list of A values.
    ///
    /// For example, given `oneOrMore(Codec.STRING)`, it accepts both `"Hello World"` and `["Hello", "World"]`.
    static <A> Codec<List<A>> oneOrMore(Codec<A> codec) {
        return Codec.either(codec, codec.listOf())
                .xmap(either -> either.map(List::of, Function.identity()),
                        list -> list.size() == 1 ? Either.left(list.getFirst()) : Either.right(list));
    }
}
