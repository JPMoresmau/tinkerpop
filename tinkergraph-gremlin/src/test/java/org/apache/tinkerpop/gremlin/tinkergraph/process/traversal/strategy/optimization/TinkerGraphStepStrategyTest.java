/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.tinkergraph.process.traversal.strategy.optimization;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.optimization.FilterRankingStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.optimization.InlineFilterStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.DefaultTraversalStrategies;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.process.traversal.step.sideEffect.TinkerGraphStep;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.apache.tinkerpop.gremlin.process.traversal.P.eq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.gt;
import static org.apache.tinkerpop.gremlin.process.traversal.P.lt;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.filter;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.not;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.properties;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.values;
import static org.junit.Assert.assertEquals;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Daniel Kuppitz (http://gremlin.guru)
 */

@RunWith(Parameterized.class)
public class TinkerGraphStepStrategyTest {

    @Parameterized.Parameter(value = 0)
    public Traversal original;

    @Parameterized.Parameter(value = 1)
    public Traversal optimized;

    @Parameterized.Parameter(value = 2)
    public Collection<TraversalStrategy> otherStrategies;

    @Test
    public void doTest() {
        final TraversalStrategies strategies = new DefaultTraversalStrategies();
        strategies.addStrategies(TinkerGraphStepStrategy.instance());
        for (final TraversalStrategy strategy : this.otherStrategies) {
            strategies.addStrategies(strategy);
        }
        this.original.asAdmin().setStrategies(strategies);
        this.original.asAdmin().applyStrategies();
        assertEquals(this.optimized, this.original);
    }

    private static GraphTraversal.Admin<?, ?> g_V(final Object... hasKeyValues) {
        final GraphTraversal.Admin<?, ?> traversal = new DefaultGraphTraversal<>();
        final TinkerGraphStep<Vertex, Vertex> graphStep = new TinkerGraphStep<>(new GraphStep<>(traversal, Vertex.class, true));
        for (int i = 0; i < hasKeyValues.length; i = i + 2) {
            graphStep.addHasContainer(new HasContainer((String) hasKeyValues[i], (P) hasKeyValues[i + 1]));
        }
        return traversal.addStep(graphStep);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> generateTestParameters() {
        return Arrays.asList(new Object[][]{
                {__.V().out(), g_V().out(), Collections.emptyList()},
                {__.V().has("name", "marko").out(), g_V("name", eq("marko")).out(), Collections.emptyList()},
                {__.V().has("name", "marko").has("age", gt(31).and(lt(10))).out(),
                        g_V("name", eq("marko"), "age", gt(31), "age", lt(10)).out(), Collections.emptyList()},
                {__.V().has("name", "marko").or(has("age"), has("age", gt(32))).has("lang", "java"),
                        g_V("name", eq("marko"), "lang", eq("java")).or(has("age"), has("age", gt(32))), Collections.singletonList(FilterRankingStrategy.instance())},
                {__.V().has("name", "marko").as("a").or(has("age"), has("age", gt(32))).has("lang", "java"),
                        g_V("name", eq("marko")).as("a").or(has("age"), has("age", gt(32))).has("lang", "java"), Collections.emptyList()},
                {__.V().has("name", "marko").as("a").or(has("age"), has("age", gt(32))).has("lang", "java"),
                        g_V("name", eq("marko"), "lang", eq("java")).as("a").or(has("age"), has("age", gt(32))), Collections.singletonList(FilterRankingStrategy.instance())},
                {__.V().dedup().has("name", "marko").or(has("age"), has("age", gt(32))).has("lang", "java"),
                        g_V("name", eq("marko"), "lang", eq("java")).or(has("age"), has("age", gt(32))).dedup(), Collections.singletonList(FilterRankingStrategy.instance())},
                {__.V().as("a").dedup().has("name", "marko").or(has("age"), has("age", gt(32))).has("lang", "java"),
                        g_V("name", eq("marko"), "lang", eq("java")).as("a").or(has("age"), has("age", gt(32))).dedup(), Collections.singletonList(FilterRankingStrategy.instance())},
                {__.V().as("a").has("name", "marko").as("b").or(has("age"), has("age", gt(32))).has("lang", "java"),
                        g_V("name", eq("marko"), "lang", eq("java")).as("a", "b").or(has("age"), has("age", gt(32))), Collections.singletonList(FilterRankingStrategy.instance())},
                {__.V().as("a").dedup().has("name", "marko").or(has("age"), has("age", gt(32))).filter(has("name", "bob")).has("lang", "java"),
                        g_V("name", eq("marko"), "name", eq("bob"), "lang", eq("java")).as("a").or(has("age"), has("age", gt(32))).dedup(), Arrays.asList(InlineFilterStrategy.instance(), FilterRankingStrategy.instance())},
                {__.V().as("a").dedup().has("name", "marko").or(has("age", 10), has("age", gt(32))).filter(has("name", "bob")).has("lang", "java"),
                        g_V("name", eq("marko"), "name", eq("bob"), "lang", eq("java")).as("a").or(has("age", 10), has("age", gt(32))).dedup(), TraversalStrategies.GlobalCache.getStrategies(TinkerGraph.class).toList()},
                {__.V().has("name", "marko").or(not(has("age")), has("age", gt(32))).has("name", "bob").has("lang", "java"),
                        g_V("name", eq("marko"), "name", eq("bob"), "lang", eq("java")).or(not(filter(properties("age"))), has("age", gt(32))), TraversalStrategies.GlobalCache.getStrategies(TinkerGraph.class).toList()}
        });
    }
}


