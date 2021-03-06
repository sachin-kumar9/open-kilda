/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.bitbucket.openkilda.messaging.info.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TODO: add javadoc.
 */
public class MeterConfigReply {

    @JsonProperty
    private long xid;

    @JsonProperty
    private List<Long> meterIds;

    @JsonCreator
    public MeterConfigReply(@JsonProperty("xid") long xid, @JsonProperty("meterIds") List<Long> meterIds) {
        this.xid = xid;
        this.meterIds = meterIds;
    }

    public long getXid() {
        return xid;
    }

    public List<Long> getMeterIds() {
        return meterIds;
    }
}
