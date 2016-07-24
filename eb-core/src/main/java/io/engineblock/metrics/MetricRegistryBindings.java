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

package io.engineblock.metrics;

import com.codahale.metrics.*;
import io.engineblock.script.ReadOnlyBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MetricRegistryBindings extends ReadOnlyBindings implements MetricRegistryListener {

    private final static Logger logger = LoggerFactory.getLogger(MetricRegistryBindings.class);
    private final MetricRegistry registry;
    private MetricMap metricMap = new MetricMap("ROOT");
    private boolean failfast = true;

    private MetricRegistryBindings(MetricRegistry registry) {
        this.registry = registry;
    }

    public static MetricRegistryBindings forRegistry(MetricRegistry registry) {
        MetricRegistryBindings mrb = new MetricRegistryBindings(registry);
        registry.addListener(mrb);
        return mrb;
    }

    @Override
    public int size() {
        return metricMap.map.size();
    }

    @Override
    public boolean isEmpty() {
        return metricMap.map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return metricMap.map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return metricMap.map.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        Object o = metricMap.map.get(key);
        if (o==null) {
            logger.info("fishing for a metric with '" + key + "'? we have:" + this.keySet());
        }
        return o;
    }

    @Override
    public Set<String> keySet() {
        return metricMap.map.keySet();
    }

    @Override
    public Collection<Object> values() {
        return metricMap.map.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return metricMap.map.entrySet();
    }

    @Override
    public void onGaugeAdded(String name, Gauge<?> metric) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.put(nodeNameOf(name), metric);
    }

    private String nodeNameOf(String name) {
        String[] split = name.split("\\.");
        return split[split.length - 1];
    }

    private synchronized void cleanEmptyMaps(MetricMap m) {
        while (m.isEmpty() && m.parent!=null) {
            logger.debug("removing empty map:" + m.name);
            MetricMap parent = m.parent;
            m.parent=null;
            parent.map.remove(m.name);
            m = parent;
        }
    }

    private synchronized MetricMap findParentNodeOf(String fullName) {
        String[] names = fullName.split("\\.");
        MetricMap m = metricMap;
        for (int i = 0; i < names.length - 1; i++) {
            String edge = names[i];
            if (m.map.containsKey(edge)) {
                Object o = m.map.get(edge);
                if (o instanceof MetricMap) {
                    m = (MetricMap) m.map.get(edge);
                    logger.info("traversing edge:" + edge + " while pathing to " + fullName);

                } else {
                    String error = "edge exists at level:" + i + ", while pathing to " + fullName;
                    logger.error(error);
                    if (failfast) {
                        throw new RuntimeException(error);
                    }
                }
            } else {
                MetricMap newMap = new MetricMap(edge,m);
                m.map.put(edge, newMap);
                m = newMap;
                logger.debug("adding edge:" + edge + " while pathing to " + fullName);
            }
        }
        return m;
    }

    @Override
    public void onGaugeRemoved(String name) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.remove(nodeNameOf(name));
        cleanEmptyMaps(parent);
    }

    @Override
    public void onCounterAdded(String name, Counter metric) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.put(nodeNameOf(name), metric);

    }

    @Override
    public void onCounterRemoved(String name) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.remove(nodeNameOf(name));
        cleanEmptyMaps(parent);
    }

    @Override
    public void onHistogramAdded(String name, Histogram metric) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.put(nodeNameOf(name), metric);

    }

    @Override
    public void onHistogramRemoved(String name) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.remove(nodeNameOf(name));
        cleanEmptyMaps(parent);

    }

    @Override
    public void onMeterAdded(String name, Meter metric) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.put(nodeNameOf(name), metric);

    }

    @Override
    public void onMeterRemoved(String name) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.remove(nodeNameOf(name));
        cleanEmptyMaps(parent);

    }

    @Override
    public void onTimerAdded(String name, Timer metric) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.put(nodeNameOf(name), metric);

    }

    @Override
    public void onTimerRemoved(String name) {
        MetricMap parent = findParentNodeOf(name);
        parent.map.remove(nodeNameOf(name));
        cleanEmptyMaps(parent);

    }

    private class MetricMap extends ReadOnlyBindings {
        Map<String, Object> map = new HashMap<String, Object>();
        MetricMap parent = null;
        public String name;

        MetricMap(String name) {
            this.name = name;
        }

        public MetricMap(String name, MetricMap parent) {
            this.parent = parent;
            this.name = name;
        }

        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return map.containsValue(value);
        }

        @Override
        public Object get(Object key) {
            return map.get(key);
        }

        @Override
        public Set<String> keySet() {
            return map.keySet();
        }

        @Override
        public Collection<Object> values() {
            return map.values();
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return map.entrySet();
        }

    }
}
