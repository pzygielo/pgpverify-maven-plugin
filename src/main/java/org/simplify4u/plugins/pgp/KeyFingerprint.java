/*
 * Copyright 2021 Slawomir Jaranowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simplify4u.plugins.pgp;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.simplify4u.plugins.utils.HexUtils;

/**
 * Represent a key fingerprint.
 */
@Value
@RequiredArgsConstructor
public class KeyFingerprint {

    byte[] fingerprint;

    /**
     * Costruct a key fingerprint from string representation.
     * @param strFingerprint a key fingerprint as string
     */
    public KeyFingerprint(String strFingerprint) {
        fingerprint = HexUtils.stringToFingerprint(strFingerprint);
    }

    public String toString() {
        return HexUtils.fingerprintToString(fingerprint);
    }
}
