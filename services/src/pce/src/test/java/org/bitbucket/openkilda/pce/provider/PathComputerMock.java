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

package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.messaging.error.CacheException;
import org.bitbucket.openkilda.messaging.error.ErrorType;
import org.bitbucket.openkilda.messaging.info.event.IslInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathInfoData;
import org.bitbucket.openkilda.messaging.info.event.PathNode;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;

import com.google.common.graph.MutableNetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PathComputerMock implements PathComputer {
    private MutableNetwork<SwitchInfoData, IslInfoData> network;

    @Override
    public Long getWeight(IslInfoData isl) {
        return 1L;
    }

    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(Flow flow) {
        SwitchInfoData source = network.nodes().stream()
                .filter(sw -> sw.getSwitchId().equals(flow.getSourceSwitch())).findFirst().orElse(null);
        if (source == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not find path",
                    String.format("Error: No node found source=%s", flow.getSourceSwitch()));
        }

        SwitchInfoData destination = network.nodes().stream()
                .filter(sw -> sw.getSwitchId().equals(flow.getDestinationSwitch())).findFirst().orElse(null);
        if (destination == null) {
            throw new CacheException(ErrorType.NOT_FOUND, "Can not find path",
                    String.format("Error: No node found destination=%s", flow.getDestinationSwitch()));
        }

        return new ImmutablePair<>(
                path(source, destination, flow.getBandwidth()),
                path(destination, source, flow.getBandwidth()));
    }

    @Override
    public ImmutablePair<PathInfoData, PathInfoData> getPath(SwitchInfoData source, SwitchInfoData destination,
                                                             int bandwidth) {
        PathInfoData forwardPath = path(source, destination, bandwidth);
        PathInfoData reversePath = path(destination, source, bandwidth);
        return new ImmutablePair<>(forwardPath, reversePath);
    }

    private PathInfoData path(SwitchInfoData srcSwitch, SwitchInfoData dstSwitch, int bandwidth) {
        System.out.println("Get Path By Switch Instances " + bandwidth + ": " + srcSwitch + " - " + dstSwitch);

        LinkedList<IslInfoData> islInfoDataLinkedList = new LinkedList<>();
        List<PathNode> nodes = new ArrayList<>();
        PathInfoData path = new PathInfoData(0L, nodes);

        if (srcSwitch.equals(dstSwitch)) {
            return path;
        }

        Set<SwitchInfoData> nodesToProcess = new HashSet<>(network.nodes());
        Set<SwitchInfoData> nodesWereProcess = new HashSet<>();
        Map<SwitchInfoData, ImmutablePair<SwitchInfoData, IslInfoData>> predecessors = new HashMap<>();

        Map<SwitchInfoData, Long> distances = network.nodes().stream()
                .collect(Collectors.toMap(k -> k, v -> Long.MAX_VALUE));

        distances.put(srcSwitch, 0L);

        while (!nodesToProcess.isEmpty()) {
            SwitchInfoData source = nodesToProcess.stream()
                    .min(Comparator.comparingLong(distances::get))
                    .orElseThrow(() -> new CacheException(ErrorType.NOT_FOUND,
                            "Can not find path", "Error: No nodes to process left"));
            nodesToProcess.remove(source);
            nodesWereProcess.add(source);

            for (SwitchInfoData target : network.successors(source)) {
                if (!nodesWereProcess.contains(target)) {
                    IslInfoData edge = network.edgesConnecting(source, target).stream()
                            .filter(isl -> isl.getAvailableBandwidth() >= bandwidth)
                            .findFirst()
                            .orElseThrow(() -> new CacheException(ErrorType.NOT_FOUND,
                                    "Can not find path", "Error: No enough bandwidth"));

                    Long distance = distances.get(source) + getWeight(edge);
                    if (distances.get(target) >= distance) {
                        distances.put(target, distance);
                        nodesToProcess.add(target);
                        predecessors.put(target, new ImmutablePair<>(source, edge));
                    }
                }
            }
        }

        ImmutablePair<SwitchInfoData, IslInfoData> nextHop = predecessors.get(dstSwitch);
        if (nextHop == null) {
            return null;
        }
        islInfoDataLinkedList.add(nextHop.getRight());

        while (predecessors.get(nextHop.getLeft()) != null) {
            nextHop = predecessors.get(nextHop.getLeft());
            islInfoDataLinkedList.add(nextHop.getRight());
        }

        Collections.reverse(islInfoDataLinkedList);

        int i = 0;
        for (IslInfoData isl : islInfoDataLinkedList) {
            collect(isl, path, i);
            i += 2;
        }

        updatePathBandwidth(path, bandwidth, islInfoDataLinkedList);

        return path;
    }

    @Override
    public PathComputer withNetwork(MutableNetwork<SwitchInfoData, IslInfoData> network) {
        this.network = network;
        return this;
    }

    @Override
    public void init() {

    }

    private void updatePathBandwidth(PathInfoData path, int bandwidth, LinkedList<IslInfoData> islInfoDataLinkedList) {
        System.out.println("Update Path Bandwidth " + bandwidth + ": " + path);
        islInfoDataLinkedList.forEach(isl -> isl.setAvailableBandwidth(isl.getAvailableBandwidth() - bandwidth));
    }

    private void collect(IslInfoData isl, PathInfoData path, int seqId) {
        PathNode source = new PathNode(isl.getPath().get(0));
        source.setSeqId(seqId);
        source.setSegLatency(isl.getLatency());
        path.getPath().add(source);

        PathNode destination = new PathNode(isl.getPath().get(1));
        destination.setSeqId(seqId + 1);
        path.getPath().add(destination);

        path.setLatency(path.getLatency() + isl.getLatency());
    }
}
