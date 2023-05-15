package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import io.atlassian.fugue.Pair;
import uk.ac.bris.cs.scotlandyard.model.*;

public class JarvisX implements Ai {

	private ImmutableValueGraph<Integer, ImmutableSet<ScotlandYard.Transport>> map;
	private long timeLimit;
	private long startTime;

	// HashMap of distances to each node in the map for a given Jarvis X location
	private static HashMap<Integer, List<Integer>> distancesMap;

	{
		distancesMap = new HashMap<>();
	}

	@Nonnull @Override public String name() { return "Jarvis X"; }

	@Nonnull @Override public Move pickMove(
			@Nonnull Board board,
			Pair<Long, TimeUnit> timeoutPair) {
		this.timeLimit = timeoutPair.left() * 1000;
		this.startTime = System.currentTimeMillis();
		this.map = board.getSetup().graph;
		int doubleTickets = board.getPlayerTickets(Piece.MrX.MRX).get().getCount(ScotlandYard.Ticket.DOUBLE);
		ImmutableList<Move> moves = board.getAvailableMoves().asList();
		List<Integer> occupied = getDetectiveLocations(board);
		HashMap<Move, Integer> singleMoves = new HashMap<>();
		HashMap<Move, Integer> doubleMoves = new HashMap<>();
		int source = extractMoveInfo(moves, singleMoves, doubleMoves);
		int maxDepth = 5;
		Pair<Integer, Integer> bestPair = maximiser(source, occupied, 1, maxDepth, Integer.MIN_VALUE, Integer.MAX_VALUE, doubleTickets);
		int bestLocation = bestPair.left();
		List<Move> bestMoves = new ArrayList<>();
		// Find all the single moves that go to the best location
		for (Map.Entry<Move, Integer> move : singleMoves.entrySet() ) {
			if (move.getValue() == bestLocation) bestMoves.add(move.getKey());
		}
		// Find all the double moves if the best location cannot be reached using a single move
		if (bestMoves.size() == 0) {
			for (Map.Entry<Move, Integer> move : doubleMoves.entrySet() ) {
				if (move.getValue() == bestLocation) bestMoves.add(move.getKey());
			}
		}
		Move chosen = bestMoves.get(new Random().nextInt(bestMoves.size()));
		return chosen;
	}


	// Iterates through Jarvis X's moves, keeps track of available locations to move to
	// and returns Jarvis X's current location
	private int extractMoveInfo(ImmutableList<Move> moves, HashMap<Move, Integer> singleMoves, HashMap<Move, Integer> doubleMoves) {
		int source = 0;
		for (Move move : moves) {
			int location = move.visit(new Move.Visitor<Integer>() {
				HashMap<Move, Integer> singleMoves;
				HashMap<Move, Integer> doubleMoves;

				public Move.Visitor<Integer> initialise(HashMap<Move, Integer> singleMoves, HashMap<Move, Integer> doubleMoves) {
					this.singleMoves = singleMoves;
					this.doubleMoves = doubleMoves;
					return this;
				}

				@Override
				public Integer visit(Move.SingleMove move) {
					this.singleMoves.put(move, move.destination);
					return move.destination;
				}

				@Override
				public Integer visit(Move.DoubleMove move) {
					this.doubleMoves.put(move, move.destination2);
					return move.destination2;
				}
			}.initialise(singleMoves, doubleMoves));
			source = move.source();
		}
		return source;
	}


	private Pair<Integer, Integer> maximiser(Integer jarvisXLocation, List<Integer> detectiveLocations, int depth, int maxDepth, int alpha, int beta, int doubleTickets) {
		Set<Integer> singleLocations = map.adjacentNodes(jarvisXLocation);
		Set<Integer> doubleLocations = new HashSet<>();
		Set<Integer> locations;
		boolean allSingleLocationsDeadly = false;
		if (depth == 1) { // if on top level of game tree, prioritise locations
			TreeSet<Integer> sortedLocations = jarvisXLocationPriority(detectiveLocations, singleLocations);
			locations = sortedLocations;
			// If the score of the best location is negative this indicates that all the single locations are deadly, hence check double locations
			if (score(sortedLocations.first(), detectiveLocations) < 0) allSingleLocationsDeadly = true;
		}
		else {
			locations = new HashSet<>(singleLocations);
		}
		// Limits the frequency of double tickets used
		if ((doubleTickets >= 1 && medianDetectiveDistance(jarvisXLocation, detectiveLocations) <= 3 && singleLocations.size() <= 4)
			|| (doubleTickets >= 1 && allSingleLocationsDeadly == true) ) {
			for (Integer adjacentNode : singleLocations) {
				if (!detectiveLocations.contains(adjacentNode)) doubleLocations.addAll(map.adjacentNodes(adjacentNode));
			}
			locations.addAll(doubleLocations);
		}
		int maxScore = Integer.MIN_VALUE;
		int bestLocation = 0;
		int score;
		for (Integer location : locations) {
			// If the immediate location to Jarvis X is occupied by a detective do not consider it
			if (!detectiveLocations.contains(location) || depth != 1) {
				if (bestLocation == 0) bestLocation = location;
				if (depth == maxDepth) {
					score = score(location, detectiveLocations);
				} else {
					int newDoubleTickets = doubleTickets;
					// If a double ticket was used, decrement the count the next subtree
					if (doubleLocations.contains(location)) newDoubleTickets -= 1;
					score = minimiser(location, detectiveLocations, depth + 1, maxDepth, alpha, beta, newDoubleTickets);
				}
				if (score > maxScore) {
					bestLocation = location;
					maxScore = score;
				}
				if (score > alpha) alpha = score;
				if (beta <= alpha) {
					return new Pair<>(bestLocation, maxScore);
				}
				// If time passed is close to the limit, then short circuit - return best move seen so far
				if (System.currentTimeMillis() - this.startTime > (this.timeLimit - 750)) {
					return new Pair<>(bestLocation, maxScore);
				}
			}
		}
		Pair<Integer, Integer> bestMove = new Pair<>(bestLocation, maxScore);
		return bestMove;
	}

	// Sorts the immediate locations to Jarvis X in order of score descending
	private TreeSet<Integer> jarvisXLocationPriority(List<Integer> detectiveLocations, Set<Integer> locations) {
		TreeSet<Integer> sortedSet = new TreeSet<>(new Comparator<Integer>() {
			private List<Integer> detectiveLocations;

			public Comparator<Integer> initialise(List<Integer> detectives) {
				this.detectiveLocations = detectives;
				return this;
			}

			@Override
			public int compare(Integer location1, Integer location2) {
				int score1 = score(location1, detectiveLocations);
				int score2 = score(location2, detectiveLocations);
				if (score1 == Integer.MIN_VALUE) score1 = -10000;
				if (score2 == Integer.MIN_VALUE) score2 = -10000;
				return  -1 * (score1 - score2);
			}
		}.initialise(detectiveLocations));
		sortedSet.addAll(locations);
		return sortedSet;
	}


	private int minimiser(Integer jarvisXLocation, List<Integer> detectiveLocations, int depth, int maxDepth, int alpha, int beta, int doubleTickets) {
		List<List<Integer>> combinations = findCombinations(detectiveLocations, jarvisXLocation);
		int minScore = Integer.MAX_VALUE;
		int score;
		for (List<Integer> combination : combinations) {
			score = maximiser(jarvisXLocation, combination, depth +1, maxDepth, alpha, beta, doubleTickets).right();
			if (combination.contains(jarvisXLocation)) {
				// Decrement the score if detectives can catch Jarvis X in their future moves
				score -= 500;
			}
			if (score < minScore) {
				minScore = score;
			}
			if (score < beta) beta = score;
			if (beta <= alpha) {
				return minScore;
			}
			// If time passed is close to the limit, then short circuit - return best move seen so far
			if (System.currentTimeMillis() - this.startTime > (this.timeLimit - 750)) {
				return minScore;
			}
		}
		return minScore;
	}


	// Iterates through a detective's moves and removes the moves that increase detective's distance to Jarvis X
	private Set<Integer> reduceCombinations(Integer currLocation, Set<Integer> adjacent, Integer jarvisXLocation) {
		int currDistance = singleDetectiveDistance(jarvisXLocation, currLocation);
		Set<Integer> reducedAdjacent = new HashSet<>();
		for (Integer adjacentNode : adjacent) {
			if (singleDetectiveDistance(jarvisXLocation, adjacentNode) <= currDistance) {
				reducedAdjacent.add(adjacentNode);
			}
		}
		if (reducedAdjacent.size() <= 1 || reducedAdjacent.size() < 0.3 * adjacent.size()) {
			reducedAdjacent = adjacent;
		}
		return reducedAdjacent;
	}


	// Finds the possible combinations of how detectives can move in the next round, disregarding bad moves
	private List<List<Integer>> findCombinations(List<Integer> detectiveLocations, Integer jarvisXLocation){
		List<List<Integer>> combinations = new ArrayList<>();
		for (Integer location : detectiveLocations) {
			Set<Integer> adjacent = map.adjacentNodes(location);
			adjacent = reduceCombinations(location, adjacent, jarvisXLocation);
			List<List<Integer>> newCombinations = new ArrayList<>();
			for (Integer adjacentNode : adjacent) {
					if (combinations.size() == 0) {
						combinations.add(new ArrayList<>(adjacentNode));
					} else {
						for (List<Integer> combination : combinations) {
							List<Integer> tempLocations = new ArrayList<>();
							tempLocations.addAll(combination);
							tempLocations.add(adjacentNode);
							newCombinations.add(tempLocations);
						}
					}
			}
			combinations = newCombinations;
		}
		return combinations;
	}


	// Returns the list of nodes occupied by the detectives
	private List<Integer> getDetectiveLocations(Board board) {
		ImmutableSet<Piece> players = board.getPlayers();
		List<Integer> occupied = new ArrayList<>();
		for (Piece player : players) {
			if (player.isDetective()) {
				Optional<Integer> detectiveLocation;
				detectiveLocation = board.getDetectiveLocation((Piece.Detective) player);
				if (detectiveLocation.isPresent()) occupied.add(detectiveLocation.get());
			}
		}
		return occupied;
	}


	// Returns the score given a location
	private int score(int location, List<Integer> occupied) {
		// Checks if the given node would be occupied
		if (occupied.contains(location)) return Integer.MIN_VALUE;
		int score = 0;
		Set<Integer> adjacent =  map.adjacentNodes(location);
		for (Integer adjacentNode : adjacent) {
			// Decrement the score for each adjacent detective
			if (occupied.contains(adjacentNode)) score -= 500;
			// Increment the score for each unoccupied adjacent node
			else score += 10;
		}
		double distance = medianDetectiveDistance(location, occupied);
		score = (int) (score + Math.floor(10 * distance));
		return score;
	}


	// Finds the distance from Jarvis X to the given detective
	private int singleDetectiveDistance(int source, int detective) {
		int distance;
		if (distancesMap.containsKey(source)) {
			distance = distancesMap.get(source).get(detective);
		}
		else {
			distancesMap.put(source, findDistances(source));
			distance = distancesMap.get(source).get(detective);
		}
		return distance;
	}


	// Returns the median distance to all detectives from the current Jarvis X location
	private double medianDetectiveDistance(int source, List<Integer> occupied) {
		List<Integer> distances;
		if (distancesMap.containsKey(source)) {
			distances = distancesMap.get(source);
		}
		else {
			distances = findDistances(source);
			distancesMap.put(source, distances);
		}
		List<Integer> detectiveDistances = new ArrayList<>();
		for (Integer detectiveLocation : occupied) {
			detectiveDistances.add(distances.get(detectiveLocation));
		}
		// Sort the detective distances to find the median
		detectiveDistances = detectiveDistances.stream().sorted((x, y) -> x - y).collect(Collectors.toList());
		double median;
		if (detectiveDistances.size() % 2 == 1) median = detectiveDistances.get(Math.floorDiv(detectiveDistances.size(), 2));
		else {
			double mid1 = detectiveDistances.get(Math.floorDiv(detectiveDistances.size(), 2));
			double mid2 = detectiveDistances.get(Math.floorDiv(detectiveDistances.size(), 2) + 1);
			median = (mid1 + mid2) / 2;
		}
		return median;
	}


	// Dijkstra's Shortest Path
	// Calculates the distance to every node from the given source node
	private List<Integer> findDistances(int source) {
		HashSet<Integer> visited = new HashSet<>();
		List<Integer> distances = new ArrayList<>();
		// Initialise distance to all nodes to infinity
		for (int i = 0; i < 200; i++) {
			distances.add(Integer.MAX_VALUE);
		}
		distances.set(source, 0);
		// Finds the shortest path to every node in the map from the given source
		while (visited.size() != map.nodes().size()) {
			for (Integer location : map.adjacentNodes(source)) {
				if (distances.get(source) + 1 < distances.get(location)) {
					distances.set(location, distances.get(source) + 1);
				}
			}
			visited.add(source);
			source = topOfPriorityQueue(distances, visited);
		}
		return distances;
	}


	// Returns the closest node to the given source node which has not been checked yet
	private int topOfPriorityQueue(List<Integer> distances, HashSet<Integer> visited) {
		int min = Integer.MAX_VALUE;
		int top = -1;
		for (int i = 1; i < distances.size(); i++) {
			if (distances.get(i) < min && !visited.contains(i)) {
				top = i;
			}
		}
		return top;
	}
}
