package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableSet;
import com.google.common.graph.*;
import io.atlassian.fugue.Pair;

import uk.ac.bris.cs.scotlandyard.model.*;

import static java.lang.Integer.*;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.SECRET;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport.FERRY;

public class Happiness implements Ai {
	MutableValueGraph<Integer, Integer> scoreGraph;
	private javafx.util.Pair<Integer, Integer> bestMove;
	public ImmutableSet<Piece> getDetectivePieces(Board b){
		b.getPlayers();
		List<Piece> list =  (b.getPlayers().stream().filter(Piece::isDetective)).toList();
		return ImmutableSet.copyOf(list);
	}
	// returns all the possible single moves for a given player
	// adapted from MyGameStateFactory, but adjusted to use pieces instead of players.
	private static java.util.Set<Move.SingleMove> getSingleMoves(GameSetup setup, ImmutableSet<Piece> detectives, Piece piece, int source, Board board, ArrayList<Integer> detLocations){
		// Creates an empty HashSets of SingleMoves to store all the SingleMove we generate
		HashSet<Move.SingleMove> movesHashSet = new HashSet<>();
		for(int destination : setup.graph.adjacentNodes(source)) {
			boolean destinationOccupied = false;
			// for every detective, get their location
			// check if equal to destination
			// if one is, go to next destination
			for (int i = 0; i< detectives.size(); i++) {
				if (detLocations.get(i).equals(destination)) {
					destinationOccupied = true;
					break;
				}
			}
			if (!destinationOccupied) {
				for (ScotlandYard.Transport t : Objects.requireNonNull(setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()))) {
					//	find out if the player has the required tickets
					//  if it does, constructs a SingleMove and adds it the collection of moves to return
					if (board.getPlayerTickets(piece).stream().anyMatch(m -> m.getCount(t.requiredTicket())!= 0)) {
						Move.SingleMove newMove = new Move.SingleMove(piece, source, t.requiredTicket(), destination);
						movesHashSet.add(newMove);
					}
				}
			}
			// if mrx has a secret move, add the secret versions of all moves.
			if (piece.isMrX() && board.getPlayerTickets(piece).get().getCount(SECRET) != 0 && !destinationOccupied){
				movesHashSet.add(new Move.SingleMove(piece,source,SECRET, destination));
			}
		}
		return movesHashSet;
	}

	// calculates all the double moves, given the set of singleMoves
	private  java.util.Set<Move.DoubleMove> getDoubleMoves(java.util.Set<Move.SingleMove> singleMovesSet, GameSetup setup, ImmutableSet<Piece> detectives, Piece piece, int source, Board board, ArrayList<Integer> detLocations){
		//initialising the sets
		HashSet<Move.DoubleMove> doubleMoveSet = new HashSet<>();
		Move.DoubleMove myDoubleMove;
		// if mrX has the double piece and not all the moves are used
		if (board.getPlayerTickets(piece).stream().anyMatch(m -> m.getCount(DOUBLE)!= 0 && board.getMrXTravelLog().size() < setup.moves.size()-1)) {
			// iterates through the list of singleMoves and finds the secondary moves for each of them
			for (Move.SingleMove singleMove : singleMovesSet) {
				java.util.Set<Move.SingleMove> secondaryMoves = getSingleMoves(setup, detectives, piece, singleMove.destination, board, detLocations);
				// iterates through all the secondaryMoves and if NOT(same tickets for both moves and doesn't have two of that ticket)
				// it will create the new double move with both the single and secondary, and adds to set of doubleMoves
				for (Move.SingleMove secondaryMove : secondaryMoves) {

					if (!(singleMove.ticket == secondaryMove.ticket && !(board.getPlayerTickets(piece).get().getCount(singleMove.ticket) >= 2))){
						myDoubleMove = new Move.DoubleMove(piece,
								source,
								singleMove.ticket,
								singleMove.destination,
								secondaryMove.ticket,
								secondaryMove.destination);

						doubleMoveSet.add(myDoubleMove);
					}
				}
			}
		}
		return doubleMoveSet;
	}


	private MutableValueGraph<Integer, Integer> updateValueGraph(Board b, Integer previous, Integer target, ImmutableSet<Piece> detectives, ArrayList<ArrayList<javafx.util.Pair<Integer, Integer>>> paths, ArrayList<Integer> detLocations){
		// for each detective (that can move), it will add the distance from the detective's location to the target location
		Integer score = 0;
		for (int detNumber = 0; detNumber<detectives.size(); detNumber++){
			var allMoves = getSingleMoves(b.getSetup(),getDetectivePieces(b), detectives.asList().get(detNumber), detLocations.get(detNumber), b , detLocations);
			int finalDetNumber = detNumber;
			if (allMoves.stream().anyMatch(m -> m.commencedBy() == detectives.asList().get(finalDetNumber))) {
				score += (paths.get(detLocations.get(detNumber) - 1).get(target - 1).getValue());
			}
		}
		scoreGraph.putEdgeValue(previous, target, score);
		return scoreGraph;
	}

	private MutableValueGraph<Integer, Integer> makeValueGraph(){
		return ValueGraphBuilder.undirected().allowsSelfLoops(true).build();
	}

	private MutableValueGraph<Integer, Integer> getValueGraph(){
		return scoreGraph;
	}

	private Set<Move.SingleMove> filterBadSingleMoves(Set<Move.SingleMove> moves, ArrayList<Integer> detLocations, Board b){
		// filters move which put them on a detective
		moves = moves.stream().filter(m -> !detLocations.contains(m.destination)).collect(Collectors.toSet());
		//adjacent node to detective (excluding ferries)
		moves = moves.stream().filter(m -> b.getSetup().graph.adjacentNodes(m.destination)
						.stream().noneMatch(adjNode -> detLocations.contains(adjNode) && !b.getSetup().graph.edgeValue(adjNode, m.destination).get().contains(FERRY)))
				.collect(Collectors.toSet());
		return moves;
	}

	private Set<Move.DoubleMove> filterBadDoubleMoves(Set<Move.DoubleMove> moves, ArrayList<Integer> detLocations, Board b){
		//on
		moves = moves.stream().filter(m -> !detLocations.contains(m.destination2)).collect(Collectors.toSet());
		//adjacent node to detective (excluding ferries)
		moves = moves.stream().filter(m -> b.getSetup().graph.adjacentNodes(m.destination2)
						.stream().noneMatch(adjNode -> detLocations.contains(adjNode.intValue()) && !b.getSetup().graph.edgeValue(adjNode, m.destination2).get().equals(FERRY)))
				.collect(Collectors.toSet());
		return moves;
	}

	//return the two best minimising positions.
	private List<Pair<Integer,Integer>> bestDetectiveMoves(Board b, Piece.Detective d, Integer currentPos, Integer destination, ArrayList<ArrayList<javafx.util.Pair<Integer, Integer>>> paths, ArrayList<Integer> detLocations){
		Integer minDist;
		ArrayList<Pair<Integer,Integer>> myMoves = new ArrayList<>();
		Set<Move.SingleMove> moves = getSingleMoves(b.getSetup(),getDetectivePieces(b), d, currentPos, b,detLocations );
		for (var n : moves){
			minDist = paths.get(n.destination-1).get(destination-1).getValue();
			currentPos = n.destination;
			myMoves.add(new Pair (minDist, currentPos));
		}
		myMoves.sort(Comparator.comparing(Pair::left));
		return myMoves.subList(0, Math.min(3, myMoves.size()));
	}

	private Integer minimax(Integer position, Integer depth, Integer turn, Board b, Integer previousNode, ArrayList<ArrayList<javafx.util.Pair<Integer, Integer>>> paths,
							Integer alpha, Integer beta , ArrayList<Integer> detLocations, Pair<Long, TimeUnit> timeoutPair, long startTime){

		Integer ans;
		var detectives = getDetectivePieces(b);
		if (depth == 0 || detLocations.contains(position) || timeoutPair.left()*1000 + startTime - System.currentTimeMillis() < 10000){
			// return the distance between position and previousNode
			// get the corresponding node with the values
			// set at the POSITION-1 the DISTANCE between DETECTIVES and POSITION-1 on the VALUE GRAPH
			bestMove = paths.get(position-1).get(previousNode-1);
			scoreGraph = updateValueGraph(b, previousNode, position, getDetectivePieces(b), paths, detLocations);

			return scoreGraph.edgeValue(previousNode, position).get() ;
		}
		// finding the highest possible total distance.
		if (turn == 0){
			int maximumDistance = 0;
			var moves = getSingleMoves(b.getSetup(),getDetectivePieces(b), MRX, position, b, detLocations);
			// for every single move, recursively calls minimax with updated parameters.
			for (Move.SingleMove move : moves) {
				scoreGraph = updateValueGraph(b, previousNode, position, detectives, paths, detLocations);
				ans = minimax(move.destination, depth - 1, 1, b, position, paths, alpha, beta, detLocations, timeoutPair, startTime);
				alpha = max(alpha, ans);
				maximumDistance = max(maximumDistance, ans);
				if (scoreGraph.edgeValue(position,move.destination).isPresent()){
					scoreGraph.removeEdge(position,move.destination);
				}
				scoreGraph.putEdgeValue(position, move.destination, ans);
				if (beta <= alpha) break;
			}
			scoreGraph.putEdgeValue(previousNode, position , maximumDistance);
			return maximumDistance;
		}
		else{
			// get the detectives best moves and then update the value graph
			int minimumDistance = MAX_VALUE;
			ArrayList<Integer> newDetLocations = new ArrayList<>(detLocations);
			var moves = getSingleMoves(b.getSetup(), detectives, detectives.asList().get(turn-1),detLocations.get(turn-1),b, detLocations);
			var bestMoves = bestDetectiveMoves(b, (Piece.Detective) detectives.asList().get(turn-1), detLocations.get(turn-1),position,paths, detLocations);
			for (Move.SingleMove move : moves) {
				if (bestMoves.stream().anyMatch(m -> m.right() == move.destination)) {
					newDetLocations.set(turn - 1, move.destination);
					if (turn < detLocations.size()) {
						ans = minimax(position, depth, turn + 1, b, previousNode, paths, alpha, beta, newDetLocations, timeoutPair, startTime);
					}
					else {
						ans = minimax(position, depth, 0, b, previousNode, paths, alpha, beta, newDetLocations, timeoutPair, startTime);
					}
					//a-b pruning
					minimumDistance = min(minimumDistance, ans);
					if (ans != null) {
						beta = min(beta, ans);
					}
					if (beta <= alpha) break;
				}
			}
			scoreGraph.putEdgeValue(previousNode, position , minimumDistance);
			return minimumDistance;
		}

	}

	@Nonnull @Override public String name() {
		return "Happiness";
	}

	// returns the best possible move for the AI
	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		scoreGraph = makeValueGraph();

		Piece AIPiece;
		var moves = board.getAvailableMoves();
		AIPiece = moves.asList().get(0).commencedBy();
		Integer playerLocation = moves.stream().toList().get(0).source();

		ShortestPath s = ShortestPath.getInstance();
		s.setBoard(board);

		ArrayList<ArrayList<javafx.util.Pair<Integer, Integer>>> paths = s.getPaths();
		ArrayList<Integer> detectiveLocations = new ArrayList<>();
		for (Piece d : getDetectivePieces(board)){
			detectiveLocations.add(board.getDetectiveLocation((Piece.Detective)d).get());
		}

		var singleMoves = getSingleMoves(board.getSetup(),getDetectivePieces(board), AIPiece, playerLocation, board, detectiveLocations);
		var doubleMoves = getDoubleMoves(singleMoves,board.getSetup(), getDetectivePieces(board), AIPiece, playerLocation, board, detectiveLocations);

		MutableValueGraph<Integer, Integer> scoreGraph = getValueGraph();

		var score = minimax(playerLocation, 3, 0, board, 0, paths, MIN_VALUE, MAX_VALUE, detectiveLocations, timeoutPair, System.currentTimeMillis());

		// filtering out the moves which are not in the list of possible moves.
		var tempSingleMoves = singleMoves.stream().filter(moves::contains).collect(Collectors.toSet());
		var tempDoubleMoves = doubleMoves.stream().filter(moves::contains).collect(Collectors.toSet());

		if ((board.getSetup().moves.size() == board.getMrXTravelLog().size() - 1) && !tempDoubleMoves.isEmpty()) {
			return tempDoubleMoves.stream().toList().get(0);
		}

		tempSingleMoves = filterBadSingleMoves(singleMoves, detectiveLocations, board);
		tempDoubleMoves = filterBadDoubleMoves(doubleMoves, detectiveLocations, board);

		// if this is true and the game isn't over, it means that the AI's only moves put him adjacent to a detective.
		if (tempSingleMoves.size() == 0 && tempDoubleMoves.size() == 0){
			// find the moves which don't put him on a detective.
			for (Piece d : getDetectivePieces(board)){
				singleMoves = singleMoves.stream().filter(m -> m.destination != board.getDetectiveLocation((Piece.Detective) d).get()).collect(Collectors.toSet());
				doubleMoves = doubleMoves.stream().filter(m -> m.destination1 != board.getDetectiveLocation((Piece.Detective) d).get()).collect(Collectors.toSet());
				doubleMoves = doubleMoves.stream().filter(m -> m.destination2 != board.getDetectiveLocation((Piece.Detective) d).get()).collect(Collectors.toSet());
			}
		}
		else {
			singleMoves = tempSingleMoves;
			doubleMoves = tempDoubleMoves;
		}

		Integer playerDestination = 0;
		int max = MIN_VALUE;
		int sum;
		for (Integer node : scoreGraph.adjacentNodes(playerLocation) ){
			if (node != 0 && singleMoves.stream().anyMatch(m -> m.destination == node)) {
				sum = scoreGraph.edgeValue(node, playerLocation).get();

				if (sum > max) {
					max = sum;
					playerDestination = node;
				}
			}
		}
		for (Integer node : scoreGraph.adjacentNodes(playerLocation) ) {
			if(scoreGraph.edgeValue(node, playerLocation).get().equals(score)){
				playerDestination = node;
			}
		}

		// if the score is below the threshold, or when there are no edges (moves), uses double move
		if ((score < 7  || playerDestination == 0) && score != 0){
			max = MIN_VALUE;
			if (singleMoves.stream().anyMatch(m -> m.ticket == SECRET)){
				singleMoves = singleMoves.stream().filter(m -> m.ticket == SECRET).collect(Collectors.toSet());
			}
			if (doubleMoves.stream().anyMatch(m -> m.ticket1 == SECRET)){
				doubleMoves = doubleMoves.stream().filter(m -> m.ticket1 == SECRET).collect(Collectors.toSet());
			}
			if (doubleMoves.stream().anyMatch(m -> m.ticket2 == SECRET)){
				doubleMoves = doubleMoves.stream().filter(m -> m.ticket2 == SECRET).collect(Collectors.toSet());
			}
			for (var n :board.getSetup().graph.adjacentNodes(playerLocation)) {
				for (var n2 : board.getSetup().graph.adjacentNodes(n)) {
					if (scoreGraph.edgeValue(n, n2).isPresent()) {
						if (scoreGraph.edgeValue(n, n2).get() > max &&
								(doubleMoves.stream().anyMatch(m -> m.destination2 == n2))) {
							max = scoreGraph.edgeValue(n, n2).get();
							playerDestination = n2;
						}
					}
				}
			}
		}
		//filters out secret singleMoves
		else {
			Integer finalPlayerDestination = playerDestination;
			if (singleMoves.stream().anyMatch(m -> m.ticket != SECRET && m.destination == finalPlayerDestination)) {
				singleMoves = singleMoves.stream().filter(m -> m.ticket != SECRET).collect(Collectors.toSet());
			}
		}

		// get the move which corresponds to playerDestination
		for (Move.SingleMove sm : singleMoves){
			if (sm.destination == playerDestination){
				return sm;
			}
		}
		for (Move.DoubleMove dm : doubleMoves){
			if (dm.destination2 == playerDestination){
				return dm;
			}
		}
		if (singleMoves.size()!=0){
			return singleMoves.stream().toList().get(0);

		}
		else{
			// finds the detectives distances between the possible doubleMoves
			// returns the best
			Move.DoubleMove bestDm = null;
			int dmMax = MIN_VALUE;
			// get the best scoring double move
			Integer tempSum = 0;
			for (Move.DoubleMove dm: doubleMoves) {
				for (Integer dl : detectiveLocations) {
					tempSum += paths.get(dl - 1).get(dm.destination2 - 1).getValue();

				}
				if (tempSum > dmMax){
					dmMax = tempSum;
					bestDm = dm;
				}
			}
			assert bestDm != null;
			return bestDm;
		}
	}
}