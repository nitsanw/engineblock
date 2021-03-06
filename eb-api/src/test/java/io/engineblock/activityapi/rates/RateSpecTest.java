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

package io.engineblock.activityapi.rates;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class RateSpecTest {

    public void testDefaultRateSpecPattern() {
        RateSpec r = new RateSpec("523");
        assertThat(r.opsPerSec).isEqualTo(523.0d);
        assertThat(r.burstRatio).isEqualTo(1.1d);
        assertThat(r.reportCoDelay).isFalse();
    }

    public void testBurstRatioPattern() {
        RateSpec r = new RateSpec("12345,1.3");
        assertThat(r.opsPerSec).isEqualTo(12345.0d);
        assertThat(r.burstRatio).isEqualTo(1.3d);
        assertThat(r.reportCoDelay).isFalse();
    }

    public void testReportPattern() {
        RateSpec r = new RateSpec("12345,1.3,co");
        assertThat(r.opsPerSec).isEqualTo(12345.0d);
        assertThat(r.burstRatio).isEqualTo(1.3d);
        assertThat(r.reportCoDelay).isTrue();
    }

}