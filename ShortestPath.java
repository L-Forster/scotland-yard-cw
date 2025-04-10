package uk.ac.bris.cs.scotlandyard.ui.ai;

import javafx.util.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Integer.MAX_VALUE;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport.FERRY;

public class ShortestPath {

    // set of nodes and distances to every node
    private ArrayList<ArrayList<Pair<Integer,Integer>>> paths = new ArrayList<>();
    private Board board;
    private Boolean solved = false;


    private boolean Solved(){
        return solved;
    }
    public ArrayList<ArrayList<Pair<Integer, Integer>>> getPaths(){
        return paths;
    }
    public void setBoard(Board b) {
        // save a reference to board
        if (board == null) {
            board = b;
        }
        if (!solved){
            // for every node in the board, run dijkstra's
            for (Integer n1 : board.getSetup().graph.nodes()) {
                paths.add(dijkstras(board, n1));
            }
            solved = true;
        }
    }
    static ShortestPath theOnlyInstance;

    // creating a singleton constructor
    public static ShortestPath getInstance(){
        if (theOnlyInstance == null){
            theOnlyInstance = new ShortestPath();
        }
        return theOnlyInstance;
    }

    //finds the shortest distance between the source and destination
    public ArrayList<Pair<Integer, Integer>> dijkstras(Board board, Integer source){
        // if there is no discovered route, by default...
        ArrayList<Integer> visitedNodes = new ArrayList<Integer>();
        java.util.Set<Integer> unvisitedNodes = board.getSetup().graph.nodes();

        ArrayList<Pair<Integer,Integer>> shortestDistance =  new ArrayList<>(200);
        ArrayList<Integer> queue = new ArrayList<>(199);
        ArrayList<Integer> previousNode = new ArrayList<>(199);
        // sets the shortest distance to be the largest by default
        for (Integer n = 1; n < 200; n++){
            shortestDistance.add(n-1, new Pair(n,MAX_VALUE/2)); // at index n, the shortest dist is...
            previousNode.add(n-1, null);
            if (true){
                queue.add(n);
            }
        }
        shortestDistance.set(source-1, new Pair (source,0));

        while (!queue.stream().allMatch(n -> n == MAX_VALUE)) {
            // minimised distance contains the elements of shortestDistance which are in the queue.
            List<Pair<Integer,Integer>> nodesInQueue = shortestDistance.stream().filter(n -> queue.contains(n.getKey())).toList();
            // find the smallest Value
            Pair<Integer,Integer> smallestDistance = nodesInQueue.stream().reduce((p1, p2) -> p1.getValue() < p2.getValue() ? p1 : p2).get();
            Integer currentNode = smallestDistance.getKey();
            ArrayList<Integer> adjNodes = new ArrayList<Integer>(board.getSetup().graph.adjacentNodes(currentNode));
            // THE DETECTIVES CANNOT TRAVEL BY FERRY, SO THESE ARE FILTERED OUT
            adjNodes = (ArrayList<Integer>) adjNodes.stream().filter(n -> !board.getSetup().graph.edgeValue(n,currentNode).equals(FERRY)).collect(Collectors.toList());
            queue.set(currentNode-1, MAX_VALUE);
            // for every unvisited neighbour of the current node
            adjNodes.remove(visitedNodes);

            for (Integer n2 : adjNodes) {
                if (queue.contains(n2)){

                    Integer temp = shortestDistance.get(currentNode-1).getValue() + 1;

                    if (temp < shortestDistance.get(n2-1).getValue()) {

                        if (previousNode.get(n2 - 1) != null) {
                            shortestDistance.set(n2 - 1, new Pair(n2,1));
                        } else {
                            shortestDistance.set(n2 - 1, new Pair (n2, temp));
                        }
                        previousNode.set(n2 - 1, shortestDistance.get(currentNode-1).getValue() + 1);
                    }
                }
            }
            visitedNodes.add(currentNode);

        }
        return shortestDistance;
    }
}
