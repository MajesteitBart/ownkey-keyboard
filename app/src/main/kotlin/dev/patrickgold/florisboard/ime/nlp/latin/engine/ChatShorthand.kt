/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin.engine

/**
 * Common chat abbreviations that people type on purpose. They are short and close to real words ("wss" is one key
 * away from "was"), so without this list autocorrect would "fix" them. They are never autocorrected.
 */
internal object ChatShorthand {
    private val English = setOf(
        "afaik", "afk", "asap", "atm", "bc", "bf", "bff", "brb", "btw", "cya", "dm", "dms", "fomo", "fr", "ftw",
        "fwiw", "fyi", "gf", "gg", "gn", "gtg", "hbd", "idc", "idk", "ikr", "ily", "imho", "imo", "irl", "jk", "lmao",
        "lmk", "lol", "nbd", "ngl", "np", "nvm", "omg", "omw", "pls", "plz", "ppl", "rn", "rofl", "smh", "tbh", "tbf",
        "thx", "tmi", "ttyl", "ty", "tyvm", "wbu", "wtf", "wyd", "yolo", "yw",
    )

    private val Dutch = setOf(
        "aub", "bvd", "dm", "egt", "ff", "gwn", "gr", "grtz", "hvj", "hvd", "idd", "iig", "ipv", "kga",
        "kheb", "kweet", "ksnap", "lkkr", "lol", "mss", "mvg", "nvm", "ofz", "ok", "oke", "pff", "sws", "tis",
        "tog", "tzt", "vgm", "vnv", "wrm", "wrs", "wss", "wtf", "zsm", "zz",
    )

    fun contains(normalizedWord: String): Boolean = normalizedWord in English || normalizedWord in Dutch
}
