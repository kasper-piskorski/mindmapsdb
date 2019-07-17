package grakn.core.graql.gremlin;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import grakn.core.graql.gremlin.fragment.Fragment;
import grakn.core.graql.gremlin.spanningtree.Arborescence;
import grakn.core.graql.gremlin.spanningtree.graph.Node;
import grakn.core.graql.gremlin.spanningtree.graph.NodeId;
import grakn.core.graql.reasoner.utils.Pair;
import grakn.core.server.session.TransactionOLTP;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import static grakn.core.graql.gremlin.NodesUtil.nodeFragmentsWithoutDependencies;
import static grakn.core.graql.gremlin.NodesUtil.nodeVisitedDependenciesFragments;
import static grakn.core.graql.gremlin.NodesUtil.propagateLabels;

public class OptimalTreeTraversal {

    private final Map<Node, Node> parents;
    private TransactionOLTP tx;
    private Set<Node> allNodes;
    private Map<NodeId, Node> nodes;
    private Arborescence<Node> arborescence;
    int iterations;
    int productIterations;
    int shortCircuits;

    public OptimalTreeTraversal(TransactionOLTP tx, Map<NodeId, Node> nodes, Arborescence<Node> arborescence) {
        this.tx = tx;
        this.nodes = nodes;
        this.allNodes = new HashSet<>(nodes.values());
        this.arborescence = arborescence;
        this.parents = arborescence.getParents();
        iterations = 0;
        productIterations = 0;
        shortCircuits = 0;
    }

    public List<Fragment> traverse(Map<Node, Map<Node, Fragment>> edgeFragmentChildToParent) {

        Map<Node, Set<Node>> parentToChild = new HashMap<>();
        arborescence.getParents().forEach((child, parent) -> {
            if (!parentToChild.containsKey(parent)) {
                parentToChild.put(parent, new HashSet<>());
            }
            parentToChild.get(parent).add(child);
        });

        propagateLabels(parentToChild);

        Map<NodeList, Pair<Double, NodeList>> memoisedResults = new HashMap<>();

        optimalCostBottomUpStack(allNodes, memoisedResults, parentToChild);

        // extract optimal node ordering from memoised
        List<Node> nodeOrdering = extractOptimalNodeOrder(memoisedResults);

        List<Fragment> plan = orderedNodesToPlan(nodeOrdering, edgeFragmentChildToParent);

        return plan;
    }

    private List<Fragment> orderedNodesToPlan(List<Node> orderedNodes, Map<Node, Map<Node, Fragment>> edgeFragmentChildToParent) {
        List<Fragment> plan = new ArrayList<>();

        for (Node node : orderedNodes) {
            nodeFragmentsWithoutDependencies(node).forEach(fragment -> {
//                if (fragment.hasFixedFragmentCost()) {
//                    plan.add(0, fragment);
//                } else {
                    plan.add(fragment);
//                }
            });
            node.getFragmentsWithoutDependency().clear();

            // add edge fragment first
            Fragment fragment = getEdgeFragment(node, arborescence, edgeFragmentChildToParent);
            if (fragment != null) plan.add(fragment);

            // add node's dependant fragments
            nodeVisitedDependenciesFragments(node, nodes).forEach(frag -> {
                if (frag.hasFixedFragmentCost()) { plan.add(0, frag); }
                else { plan.add(frag); }
            });

        }
        return plan;
    }

    @Nullable
    private static Fragment getEdgeFragment(Node node, Arborescence<Node> arborescence,
                                            Map<Node, Map<Node, Fragment>> edgeToFragment) {
        if (edgeToFragment.containsKey(node) &&
                edgeToFragment.get(node).containsKey(arborescence.getParents().get(node))) {
            return edgeToFragment.get(node).get(arborescence.getParents().get(node));
        }
        return null;
    }

    private List<Node> extractOptimalNodeOrder(Map<NodeList, Pair<Double, NodeList>> memoisedResults) {
        List<Node> orderedNodes = new ArrayList<>();
        NodeList next = new NodeList(allNodes);
        while (true) {
            NodeList nextNodeList = memoisedResults.get(next).getValue();
            if (nextNodeList == null) {
                orderedNodes.add(next.get(0));
                break;
            } else {
                Node newNode = Iterators.getOnlyElement(Sets.difference(new HashSet<>(next), new HashSet<>(nextNodeList)).iterator());
                orderedNodes.add(newNode);
                next = nextNodeList;
            }
        }
        // we extract the nodes in reverse order, so flip
        Collections.reverse(orderedNodes);
        return orderedNodes;
    }


    public double optimalCostBottomUpStack(Set<Node> allNodes,
                                           Map<NodeList, Pair<Double, NodeList>> memoised,
                                           Map<Node, Set<Node>> edgesParentToChild) {

        Stack<StackEntry> stack = new Stack<>();

        // find all leaves as the starting removable node set
        NodeList leafNodes = leaves(parents);
        stack.push(new StackEntry(new NodeList(allNodes), leafNodes));

        // we can prune the search space by stopping the search when the current cost exceeds the best cost we have found yet
        double bestCostFound = Double.MAX_VALUE; // best tree traversal cost found yet
        double currentCost = 0.0;                // partial cost of the tree traversal we are calculating

        while (stack.size() != 0) {
            iterations++;
            StackEntry entry = stack.peek();

            // if this set of nodes has been visited once already, we are in the stack size reduction
            // phase, where entries are being popped off. This also means we have guaranteed all the
            // children of this stack entry have been calculated, we can calculate the optimal
            // cost and path to take from this `visited` node set.
            if (entry.haveVisited) {

                double cost = entry.visitedNodeListCost;

                NodeList bestChild = findBestChild(entry, memoised);

                // memoise the cost of the cost of the entry.visited set plus traversing via the chosen next child set
                double bestChildCost;
                if (bestChild == null) {
                    if (entry.visited.size() == 1) {
                        bestChildCost = 0.0;
                    } else {
                        bestChildCost = Double.MAX_VALUE;
                    }
                } else {
                    bestChildCost = memoised.get(bestChild).getKey();
                }
//                double bestChildCost = bestChild == null ? 0 : memoised.get(bestChild).getKey();
                memoised.put(entry.visited, new Pair<>(cost + bestChildCost, bestChild));

                // the current cost to completion -- cost from start of stack up to a final value
                // is currentCost + bestChildCost
                double currentCostToCompletion = currentCost + bestChildCost;

                // update the global best cost found yet if we are the top of the stack and have no more paths to explore
                if (currentCostToCompletion < bestCostFound) {
                    bestCostFound = currentCostToCompletion;
                }

                // actually remove this stack entry when we see it the second time
                stack.pop();
                // remove the cost from the current total as we have finished processing this stack entry
                currentCost -= cost;

            } else {
                // compute the cost of the current visited set of nodes
                double cost = product(entry.visited); // cost of enumerating all n-tuples for the currently visited nodes

                // if this stack entry has not been visited, before we expand all of its children onto the stack
                // and mark this entry as visited. So when we finish processing its children, we are guaranteed to
                // be able to evaluate this entry

                // set that we have visited the entry for the first time
                entry.setHaveVisited();
                entry.setVisitedNodeListCost(cost);

                // include the cost of taking this branch in the best current cost
                // so the current cost includes this branch
                currentCost += cost;

                if (currentCost >= bestCostFound) {
                    // we can short circuit the execution if we are on a more expensive branch than the best yet
                    shortCircuits++;
                    stack.pop();
                    currentCost -= cost;
                    continue;
                }

                // small optimisation: when visited has size 1, we can skip because we are going
                // to remove the last element, resulting in a 0-element next visited set
                // which we can safely skip some extra computation
                if (entry.visited.size() == 1) {
                    continue;
                }

                entry.removable.sort(Comparator.comparing(node -> node.matchingElementsEstimate(tx)));

                for (Node nextNode : entry.removable) {

                    NodeList nextVisited = copyExceptElement(entry.visited, nextNode);

                    // record that this child is a dependant of the currently stack entry
                    entry.addChild(nextVisited);

                    NodeList nextRemovable = copyExceptElement(entry.removable, nextNode);
                    Node parent = parents.get(nextNode);
                    if (parent != null) {
                        Set<Node> siblings = edgesParentToChild.get(parent);
                        // if none of the siblings are in entry.removable, we can add the parent to removable
                        // avoiding the set intersection by checking size first can save 20% of time spent in this check
                        if (siblings.size() == 1 || isEmptyIntersection(siblings, nextVisited)) {
                            nextRemovable.add(parent);
                        }
                    }

                    // compute child if we don't already have the result for the child
                    boolean isComputed = memoised.containsKey(nextVisited);
                    if (!isComputed) {
                        stack.push(new StackEntry(nextVisited, nextRemovable));
                    }
                }
            }
        }

        double finalCost = memoised.get(new NodeList(allNodes)).getKey();
        // these assertions fail because we start adding very small numbers (eg. 1) to very big doubles (eg. 3.11111E200)
//        assert currentCost == 0;
//        assert finalCost == bestCostFound;
        return finalCost;
    }


    private NodeList findBestChild(StackEntry entry, Map<NodeList, Pair<Double, NodeList>> memoised) {
        // find the best child to choose after the current entry -
        // each child has either been calculated or skipped for having too high a cost (pruned out)
        NodeList bestChild = null;
        double bestChildCost = Double.MAX_VALUE;
        // update the cost to include the path from the best to child to the finish
        // if there are any children
        for (NodeList child : entry.children) {
            Pair<Double, NodeList> memoisedResult = memoised.get(child);
            if (memoisedResult != null && (bestChild == null || memoisedResult.getKey() < bestChildCost)) {
                bestChild = child;
                bestChildCost = memoisedResult.getKey();
            }
        }
        return bestChild;
    }

    private NodeList copyExceptElement(NodeList list, Node notToInclude) {
        NodeList copy = new NodeList();
        for (Node node : list) {
            if (node != notToInclude) {
                copy.add(node);
            }
        }
        return copy;
    }

    private boolean isEmptyIntersection(Set<Node> visited, NodeList siblings) {
        for (Node node : siblings) {
            if (visited.contains(node)) {
                return false;
            }
        }
        return true;
    }

    private double product(Collection<Node> nodes) {
        productIterations++;
        if (nodes.size() == 0) {
            return 0.0;
        }

        double cost = 1.0;
        for (Node node : nodes) {
            cost = cost * node.matchingElementsEstimate(tx);
        }
        return cost;
    }

    private NodeList leaves(Map<Node, Node> parents) {
        Set<Node> keys = new HashSet<>(parents.keySet());
        keys.removeAll(parents.values());
        return new NodeList(keys);
    }

    class StackEntry {
        boolean haveVisited;
        NodeList visited;
        NodeList removable;
        List<NodeList> children;
        double visitedNodeListCost = -1; // cached product to save half the evaluations of product

        StackEntry(NodeList visited, NodeList removable) {
            this.visited = visited;
            this.removable = removable;
            haveVisited = false;
            children = new ArrayList<>();
        }

        void addChild(NodeList child) {
            this.children.add(child);
        }

        void setHaveVisited() {
            haveVisited = true;
        }

        void setVisitedNodeListCost(double product) {
            visitedNodeListCost = product;
        }

    }

    /*
    A semi-dangerous extension of List that caches hash codes
    When using, ensure that the values never change after the first call of hash codes!
    // TODO only make this available via a constructor function and make the implementation immutable
    Provides a 25-50% speedup of calls to memoised map, which has costs dominated by hashCode()
     */
    class NodeList extends ArrayList<Node> {
        int cachedHashCode = 0;

        NodeList(Collection<Node> nodes) {
            super(nodes);
        }

        NodeList() {
            super();
        }

        @Override
        public int hashCode() {
            if (cachedHashCode == 0) {
                cachedHashCode = super.hashCode();
            }
            return cachedHashCode;
        }
    }



}

