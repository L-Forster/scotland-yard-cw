package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import uk.ac.bris.cs.scotlandyard.model.*;

import static java.lang.Integer.*;
import static java.util.stream.Collectors.toList;
import static uk.ac.bris.cs.scotlandyard.model.Piece.MrX.MRX;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.SECRET;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Transport.FERRY;

// External libraries:
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.atlassian.fugue.Pair;
//
public class Louis implements Ai {

	public ImmutableSet<Piece> getDetectivePieces(Board b){
		b.getPlayers();
		List<Piece> list =  (b.getPlayers().stream().filter(Piece::isDetective)).toList();
		return ImmutableSet.copyOf(list);
	}

	//returns all the possible single moves for a given player
	// adapted from MyGameStateFactory, but adjusted to use pieces instead of players.
	private static java.util.Set<Move.SingleMove> getSingleMoves(GameSetup setup, ImmutableSet<Piece> detectives, Piece piece, int source,
																 Board board, List<Integer> detLocations){
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
	private  java.util.Set<Move.DoubleMove> getDoubleMoves(java.util.Set<Move.SingleMove> singleMovesSet, GameSetup setup,
														   ImmutableSet<Piece> detectives, Piece piece, int source, Board board, ArrayList<Integer> detLocations){
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

	private double calculateScore(Board b, Integer target, ImmutableSet<Piece> detectives,
								  ArrayList<ArrayList<javafx.util.Pair<Integer, Integer>>> paths,
								  List<Integer> detLocations, Integer remainingTurns, MCNode node){
		// for each detective (that can move), it will add the distance from the detective's location to the target location
		double totalDistance = 0;
		for (int detNumber = 0; detNumber<detectives.size(); detNumber++){
			var allMoves = getSingleMoves(b.getSetup(),getDetectivePieces(b), detectives.asList().get(detNumber), detLocations.get(detNumber), b, detLocations );
			int finalDetNumber = detNumber;
			if (allMoves.stream().anyMatch(m -> m.commencedBy() == detectives.asList().get(finalDetNumber))) {
				totalDistance += (paths.get(detLocations.get(detNumber) - 1).get(target - 1).getValue());
			}
		}
		double score;
		double adjNum = b.getSetup().graph.adjacentNodes(target).size();
		score = totalDistance/20 + 0.5*remainingTurns/b.getSetup().moves.size() + 0.1*adjNum;
		return score;
	}

	// returns every possible combination of moves for a given gameState
	private List<List<Integer>> allCombinations(List<List<Integer>> moveSet){
		List<List<Integer>> combinations;
		combinations = Lists.cartesianProduct(moveSet);
		// filter out the lists which have multiple players at one tile
		combinations = combinations.stream().filter(m -> m.stream().distinct().count() == m.size()).collect(toList());
		return combinations;
	}

	//These two methods filter out the (Single/Double)moves that put them adjacent to / on a detective.
	private Set<Move.SingleMove> filterBadSingleMoves(Set<Move.SingleMove> moves, MCState state, Board b){
		//filters where destination is a detective's location.
		moves = moves.stream().filter(m -> !state.getDetectiveLocations().contains(m.destination)).collect(Collectors.toSet());
		//adjacent node to detective (excluding ferries)
		moves = moves.stream().filter(m -> b.getSetup().graph.adjacentNodes(m.destination)
						.stream().noneMatch(adjNode -> state.getDetectiveLocations().contains(adjNode) &&
								!b.getSetup().graph.edgeValue(adjNode, m.destination).get().contains(FERRY)))
						.collect(Collectors.toSet());
		return moves;
	}

	private Set<Move.DoubleMove> filterBadDoubleMoves(Set<Move.DoubleMove> moves, MCState state, Board b){
		//filters where destination is a detective's location.
		moves = moves.stream().filter(m -> !state.getDetectiveLocations().contains(m.destination2)).collect(Collectors.toSet());
		//adjacent node to detective (excluding ferries)
		moves = moves.stream().filter(m -> b.getSetup().graph.adjacentNodes(m.destination2)
						.stream().noneMatch(adjNode -> state.getDetectiveLocations().contains(adjNode) &&
								!b.getSetup().graph.edgeValue(adjNode, m.destination2).get().contains(FERRY)))
				.collect(Collectors.toSet());
		return moves;
	}

	// Applying the UCT algorithm
	private double uct(MCNode node, Double explor){
		double solution = node.getScore()/node.getVisits() + explor * (Math.sqrt(Math.log(node.getParent().getVisits())/node.getVisits()));
		if (node.getVisits() == 0) solution = Double.MAX_VALUE;
		return solution;
	}

	// Selects the best child of the current node (to be expanded)
	private MCNode select(Double explor, MCNode node){
		if (node.getChildren().isEmpty()){
			return node;
		}
		// if there is a child with zero score it will choose a random one.
		List<MCNode> unvisitedChildren = node.getChildren().stream().filter(child -> child.getVisits() == 0).toList();
		double childScore = MIN_VALUE;
		MCNode selectedNode = node;
		if (unvisitedChildren.isEmpty()) {
			// calculates the child with the highestScore
			for (MCNode child : node.getChildren()) {
				if (uct(child, explor) > childScore) {
					childScore = uct(child, explor);
					selectedNode = child;
				}
			}
		}
		else{
			Random rnd = new Random();
			selectedNode =  (MCNode) unvisitedChildren.toArray()[rnd.nextInt(unvisitedChildren.size())];
		}
		// goes until the selected node is a leaf.
		return select(explor, selectedNode);
	}

	private MCNode MCTS(Integer depth, Double explor, MCState state, Integer turn, Board b, Pair<Long, TimeUnit> timeoutPair){
		long startTime = System.currentTimeMillis();
		long endTime = 0;
		MCNode root = new MCNode(state, null);
		root.setVisits(0);
		root.setScore(0);
		root.setState(new MCState(state.getMRXLocation(), state.getDetectiveLocations()));
		// this should run until  it runs out of time.
		while ( timeoutPair.left()*1000+ startTime - endTime > 10000){
			// initially adds the children to the empty root node.
			if (root.getChildren().size() == 0) {
				root = expand(root, state, b);
			}
			// runs select recursively
			MCNode selectedChild;
			selectedChild = select(explor, root);
			// if the child has not been visited before.. expand..
			if (selectedChild.getVisits() == 0) {
				// run expand.
				selectedChild = expand(selectedChild, state, b);
			}
			// run several simulations
			int count = 0;
			double simScoreAv = 0;
			int iterations = 500;
			while (count < iterations) {
				simScoreAv += (simulate(depth, state, b, turn, selectedChild, 1));
				count++;
			}
			// calculates the average score
			simScoreAv = simScoreAv / iterations;
			// take into consideration the win probability
			double wins = selectedChild.getWins();
			double visits = selectedChild.getVisits();
			double winProb = wins/visits;
			simScoreAv = simScoreAv + 0.5 *(winProb);
			selectedChild.setScore(simScoreAv);
			endTime = System.currentTimeMillis();
		}
		// return the location of detective of the first child of the child with the highest SCORE value.
		double bestScore = Double.MIN_VALUE;
		MCNode bestNode = null;
		for (MCNode node : root.getChildren()){
			if (node.getScore()>bestScore){
				bestScore = node.getScore();
				bestNode = node;
			}
		}
		// if bestNode is null, then it returns an invalid move. The pickMove function will detect this and return a valid move
		return bestNode;
	}

	// expand should add all the possible future game States from that move as a child of the selected node.
	// where all the players have moved.
	private MCNode expand(MCNode selectedChild, MCState state, Board b){
		// store the possible destinations for each piece, then find the combinations of them.
		// get the nodes for each player.
		ArrayList<List<Integer>> setOfMoves = new ArrayList<>();
		Set<Move.SingleMove> moves = getSingleMoves(b.getSetup(), getDetectivePieces(b), MRX, state.getMRXLocation(), b, state.getDetectiveLocations());
		moves = filterBadSingleMoves(moves, state, b);
		List<Integer> destinations = moves.stream().map(m -> m.destination).collect(toList());
		setOfMoves.add(	destinations.stream().distinct().collect(toList()));

		for (int i = 0; i < getDetectivePieces(b).size(); i++){
			moves = getSingleMoves(b.getSetup(), getDetectivePieces(b), getDetectivePieces(b).asList().get(i),
									state.getDetectiveLocations().get(i), b, state.getDetectiveLocations());
			// filter the moves, so each possible destination only appears once.
			destinations = moves.stream().map(m -> m.destination).collect(toList());
			setOfMoves.add(	destinations.stream().distinct().collect(toList()));

		}
		List<List<Integer>> combinations = allCombinations(setOfMoves);

		for (List<Integer> futurePositions : combinations){
//			// creates a new state for that child
			MCState newState = new MCState(futurePositions.get(0), futurePositions.subList(1,futurePositions.size()));
			selectedChild.setChild(new MCNode(newState, selectedChild));
		}
		return selectedChild;
	}

	// generates a random game outcome.
	private double simulate(Integer depth, MCState state,Board b, Integer turn, MCNode node, Integer moveCount){
		// If the search depth is reached
		MCState newState;
		double simScore;
		if (moveCount == b.getSetup().moves.size()){
			node.setWins(1);
			//returns the final score based on the distance to detectives and the number of moves remaining, the number of
			simScore = calculateScore(b,state.getMRXLocation(),getDetectivePieces(b), ShortestPath.getInstance().getPaths(),
										state.getDetectiveLocations(), (b.getMrXTravelLog().size()-moveCount), node);
			backpropagate(node, simScore);
			return simScore;
		}
		// getting moves
		Set<Move.SingleMove> moves;
		if (turn == 0){
			moves = getSingleMoves(b.getSetup(),getDetectivePieces(b), MRX,state.getMRXLocation(),b, state.getDetectiveLocations());
			moves = filterBadSingleMoves(moves, state, b);
		}
		else{
			moves = getSingleMoves(b.getSetup(),getDetectivePieces(b), getDetectivePieces(b).asList().get(turn-1),
									state.getDetectiveLocations().get(turn-1),b, state.getDetectiveLocations());
		}
		// if all the adjacent nodes of MRX are occupied, he has lost
		if (state.getDetectiveLocations().containsAll(b.getSetup().graph.adjacentNodes(state.getMRXLocation()))){
			simScore = 0;
			backpropagate(node, simScore);
			return simScore;
		}
		// if that location is equal to a detective's location, he has lost.
		if (state.getDetectiveLocations().contains(state.getMRXLocation())){
			simScore = 0;
			backpropagate(node, simScore);
			return simScore;
		}
		// selecting random move
		Move.SingleMove randomMove;
		Random rnd = new Random();
		if (moves.size() > 0) {
			randomMove = (Move.SingleMove) moves.toArray()[rnd.nextInt(moves.size())];
			newState = new MCState(state.getMRXLocation(), state.getDetectiveLocations());
			newState.setPlayerLocations(turn, randomMove.destination);
		}
		else {
			// when there are no moves left and MRX turn
			if (turn == 0) {
					simScore = 0;
					backpropagate(node, simScore);
					return simScore;
			}
			else {
				newState = state;
			}
		}
		// call simulate again with the updated state.
		if (turn == 0){
			return simulate(depth-1, newState, b,turn+1, node,moveCount+1);
		}
		else if (turn == getDetectivePieces(b).size() ){
			return simulate(depth, newState, b,0, node,moveCount );
		}
		else {
			return simulate(depth,newState,b,turn+1,node, moveCount);
		}
	}

	// The backpropagation stage, where the tree is backtracked, updating the nodes' information
	private void backpropagate(MCNode node, double simScore){
		while(node != null){
			node.setScore(node.getScore() + simScore);
			node.setVisits(node.getVisits()+1);
			node = node.getParent();
		}
	}

	@Nonnull @Override public String name() {
		return "Louis";
	}


	// returns the best possible move for the AI
	@Nonnull @Override public Move pickMove(@Nonnull Board board, Pair<Long, TimeUnit> timeoutPair) {
		Piece AIPiece;
		var moves = board.getAvailableMoves();
		AIPiece = moves.asList().get(0).commencedBy();
		int playerLocation = moves.stream().toList().get(0).source();

		ShortestPath s = ShortestPath.getInstance();
		s.setBoard(board);
		ArrayList<ArrayList<javafx.util.Pair<Integer, Integer>>> paths = s.getPaths();
		ArrayList<Integer> detectiveLocations = new ArrayList<>();

		for (Piece d : getDetectivePieces(board)){
			detectiveLocations.add(board.getDetectiveLocation((Piece.Detective)d).get());

		}
		var singleMoves = getSingleMoves(board.getSetup(),getDetectivePieces(board), AIPiece, playerLocation, board, detectiveLocations);
		var doubleMoves = getDoubleMoves(singleMoves,board.getSetup(), getDetectivePieces(board), AIPiece, playerLocation, board, detectiveLocations);

		MCState state = new MCState(playerLocation, detectiveLocations);
		Integer playerDestination = 0;
		// Calling Monte Carlo Tree Search
		MCNode bestNode = MCTS(board.getSetup().moves.size(),0.25, state,0,board, timeoutPair);

		if (bestNode != null){
			playerDestination = bestNode.getState().getMRXLocation();
		}

		// filtering out the moves which are not in the list of possible moves.
		var tempSingleMoves = singleMoves.stream().filter(moves::contains).collect(Collectors.toSet());
		var tempDoubleMoves = doubleMoves.stream().filter(moves::contains).collect(Collectors.toSet());
		// uses double move if 2 turns remain.
		if ((board.getSetup().moves.size() == board.getMrXTravelLog().size() - 1) && !tempDoubleMoves.isEmpty()) {
			return tempDoubleMoves.stream().toList().get(0);
		}

		// filter the moves which put MRX adjacent to a detective.
		tempSingleMoves = filterBadSingleMoves(singleMoves, state, board);
		tempDoubleMoves = filterBadDoubleMoves(doubleMoves, state, board);


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

		if (playerDestination == 0){
			if (singleMoves.stream().anyMatch(m -> m.ticket == SECRET)){
				singleMoves = singleMoves.stream().filter(m -> m.ticket == SECRET).collect(Collectors.toSet());
			}
			if (doubleMoves.stream().anyMatch(m -> m.ticket1 == SECRET)){
				doubleMoves = doubleMoves.stream().filter(m -> m.ticket1 == SECRET).collect(Collectors.toSet());
			}
			if (doubleMoves.stream().anyMatch(m -> m.ticket2 == SECRET)){
				doubleMoves = doubleMoves.stream().filter(m -> m.ticket2 == SECRET).collect(Collectors.toSet());
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
		if (singleMoves.size()!=0) {
			return singleMoves.stream().toList().get(0);
		}
		else{
			// finds the detectives distances between the possible doubleMoves
			// returns the best.
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
