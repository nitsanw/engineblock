/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package io.engineblock.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * This sequencer just repeats a number of elements, one element after
 * another.
 *
 * @param <T>
 */
public class ConcatSequencer<T> implements ElementSequencer<T> {

    @Override
    public int[] sequenceByIndex(List<T> elems, ToLongFunction<T> ratioFunc) {

        List<Integer> sequence = new ArrayList<>();

        for (int elemIndex = 0; elemIndex < elems.size(); elemIndex++) {
            long runLength = ratioFunc.applyAsLong(elems.get(elemIndex));
            for (int i = 0; i < runLength; i++) {
                sequence.add(i);
            }
        }
        return sequence.stream().mapToInt(i -> (int)i).toArray();
    }
}
