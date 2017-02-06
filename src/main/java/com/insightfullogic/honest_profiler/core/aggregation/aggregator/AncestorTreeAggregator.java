package com.insightfullogic.honest_profiler.core.aggregation.aggregator;

import static java.util.stream.Collector.of;
import static java.util.stream.Collectors.groupingBy;

import java.util.Map;

import com.insightfullogic.honest_profiler.core.aggregation.AggregationProfile;
import com.insightfullogic.honest_profiler.core.aggregation.grouping.CombinedGrouping;
import com.insightfullogic.honest_profiler.core.aggregation.result.Aggregation;
import com.insightfullogic.honest_profiler.core.aggregation.result.Keyed;
import com.insightfullogic.honest_profiler.core.aggregation.result.straight.Entry;
import com.insightfullogic.honest_profiler.core.aggregation.result.straight.Node;
import com.insightfullogic.honest_profiler.core.aggregation.result.straight.Tree;
import com.insightfullogic.honest_profiler.core.profiles.lean.LeanNode;

/**
 * Aggregator which takes an {@link Entry} and aggregates the ancestors of all the {@link LeanNode}s aggregated by that
 * {@link Entry} into a {@link Tree}.
 */
public class AncestorTreeAggregator implements SubAggregator<Entry, Node>
{
    // Aggregator Implementation

    /**
     * @see SubAggregator#aggregate(Object)
     */
    @Override
    public Tree aggregate(Entry input)
    {
        Aggregation<Keyed<String>> aggregation = input.getAggregation();
        AggregationProfile source = aggregation.getSource();
        CombinedGrouping grouping = aggregation.getGrouping();

        Tree result = new Tree(source, input.getAggregation().getGrouping());

        Node root = new Node(input);
        result.getData().add(root);

        addAncestors(source, root, result, grouping);
        return result;
    }

    /**
     * Recursive method for aggregating the parents (and ancestors) of the {@link LeanNode}s which are aggregated by the
     * provided {@link Node} and adding them as children.
     *
     * @param source the original {@link AggregationProfile}
     * @param child the input {@link Node}
     * @param tree the resulting {@link Tree}
     * @param grouping the key calculation grouping
     */
    private void addAncestors(AggregationProfile source, Node child, Tree tree,
        CombinedGrouping grouping)
    {
        Map<String, Node> result = child.getAggregatedNodes().stream().map(node -> node.getParent())
            // Parent of a root LeanNode is null
            .filter(node -> node != null)
            .collect(groupingBy(
                // Group LeanNodes by calculated key
                node -> grouping.apply(source, node),
                // Downstream collector, collects LeanNodes in a single group
                of(
                    // Supplier, creates an empty Node
                    () ->
                    {
                        Node node = new Node(tree);
                        node.setReference(source.getGlobalData());
                        return node;
                    },
                    // Accumulator, aggregates a LeanNode into the Entry accumulator
                    (node, leanNode) ->
                    {
                        node.add(leanNode);
                        node.setKey(grouping.apply(source, leanNode));
                    },
                    // Combiner, combines two Nodes with the same key
                    (node1, node2) -> node1.combine(node2)
                )
            ));

        result.entrySet().forEach(mapEntry ->
        {
            child.addChild(mapEntry.getValue());
            // Recursively add ancestors
            addAncestors(source, mapEntry.getValue(), tree, grouping);
        });
    }
}
