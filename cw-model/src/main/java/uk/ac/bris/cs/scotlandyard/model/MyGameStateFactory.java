package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javafx.util.Pair;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.Piece.*;
import uk.ac.bris.cs.scotlandyard.model.Move.*;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private final static class MyGameState implements GameState {
		private final GameSetup setup;
		private final ImmutableSet<Piece> remaining;
		private final ImmutableList<LogEntry> log;
		private final Player mrX;
		private final List<Player> detectives;
		private final ImmutableList<Player> everyone;
		private final ImmutableSet<Move> moves;
		private final ImmutableSet<Piece> winner;
		private int round;

		private MyGameState(
				final GameSetup setup,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log,
				final Player mrX,
				final List<Player> detectives) {

			checkPlayerAttributes(mrX, detectives);
			checkSetup(setup);

			this.setup = setup;
			this.remaining = remaining;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;

			List<Player> listEveryone = new ArrayList<>(detectives);
			listEveryone.add(mrX);
			ImmutableList<Player> everyone = ImmutableList.copyOf(listEveryone);
			this.everyone = everyone;

			ImmutableSet<Move> allMoves = ImmutableSet.of();
			for (Player player : everyone) {
				if (remaining.contains(player.piece())) {
					ImmutableSet<SingleMove> singleMoves = makeSingleMoves(setup, detectives, player, player.location());
					allMoves = ImmutableSet.<Move>builder()
							.addAll(singleMoves)
							.addAll(allMoves)
							.build();
				}
			}
			if (remaining.contains(MrX.MRX)) {
				ImmutableSet<DoubleMove> doubleMoves = makeDoubleMoves(setup, detectives, mrX, mrX.location());
				allMoves = ImmutableSet.<Move>builder()
						.addAll(doubleMoves)
						.addAll(allMoves)
						.build();
			}

			this.moves = allMoves;

			this.round = log.size();

			this.winner = checkGameOver();

		}

		//Checks if any of the detectives is mrX and checks if they have an illegal ticket and checks if any detective has the same piece
		//and checks if more than one detective in same location
		private void checkPlayerAttributes(final Player mrX, final List<Player> detectives) {
			if (!mrX.isMrX()) throw new IllegalArgumentException("No MrX!");
			for (Player detective : detectives) {
				if (!detective.isDetective()) throw new IllegalArgumentException("It is not a detective!");
				if (detective.has(ScotlandYard.Ticket.SECRET) || detective.has(ScotlandYard.Ticket.DOUBLE))
					throw new IllegalArgumentException("Detective has illegal ticket!");
			}
			for (int i = 0; i < detectives.size(); i++) {
				for (int j = i+1; j < detectives.size(); j++) {
					if (detectives.get(i).piece() == detectives.get(j).piece())
						throw new IllegalArgumentException("More than one detective with same colour!");
					if (detectives.get(i).location() == detectives.get(j).location())
						throw new IllegalArgumentException("More than one detective in the same location!");
				}
			}
		}

		//Checks if rounds and graph are empty
		private void checkSetup(final GameSetup setup) {
			if (setup.rounds.isEmpty()) throw new IllegalArgumentException("Empty Rounds!");
			Set<Integer> nodes = setup.graph.nodes();
			if (nodes.isEmpty()) throw new IllegalArgumentException("Empty Graph!");
		}

		// Determines whether there is a winner and returns it
		private ImmutableSet<Piece> checkGameOver() {
			// mrX wins if:
			// 1. Detectives have no more tickets left
			boolean ticketsLeft = false;
			for (Player detective : detectives) {
				ticketsLeft = detectiveHasAnyTicket(detective, ticketsLeft);
			}
			if (!ticketsLeft) return ImmutableSet.of(MrX.MRX);
			// 2. No more rounds left
			else if (round == setup.rounds.size()) return ImmutableSet.of(MrX.MRX);

			// Detectives win if:
			ArrayList<Piece> winningDetectives = new ArrayList<>();
			for (Player detective : detectives) {
				winningDetectives.add(detective.piece());
			}
			// 1. MrX is cornered and cannot make a move
			boolean mrXHasAnyMoves = false;
			for (Move move : moves) {
				if (move.commencedBy() == MrX.MRX) mrXHasAnyMoves = true;
			}
			if (mrXHasAnyMoves == false && remaining.contains(MrX.MRX)) {
				return ImmutableSet.copyOf(winningDetectives);
			}
			// 2. MrX is captured
			for (Player detective : detectives) {
				if (detective.location() == mrX.location()) return ImmutableSet.copyOf(winningDetectives);
			}

			// Otherwise the game continues
			return ImmutableSet.of();
		}

		// Works out all possible single moves for all players and returns an Immutable set of all the moves
		private static ImmutableSet<SingleMove> makeSingleMoves(
				GameSetup setup,
				List<Player> detectives,
				Player player,
				int source){
			final var singleMoves = new ArrayList<SingleMove>();
			for (int destination : setup.graph.adjacentNodes(source)) {
				boolean occupied = checkIfOccupied(destination, detectives);
				for(Transport t : setup.graph.edgeValueOrDefault(source,destination,ImmutableSet.of())) {
					if (player.has(t.requiredTicket()) && !occupied) {
						SingleMove newMove = new SingleMove(player.piece(), source, t.requiredTicket(), destination);
						singleMoves.add(newMove);
					}
				}
				if (player.isMrX() && player.has(Ticket.SECRET) && !occupied) {
					SingleMove newMove = new SingleMove(player.piece(), source, Ticket.SECRET, destination);
					singleMoves.add(newMove);
				}
			}
			return ImmutableSet.copyOf(singleMoves);
		}

		//Checks if given position is occupied by another detective
		private static boolean checkIfOccupied(int destination, List<Player> detectives) {
			for (Player detective : detectives) {
				if (detective.location() == destination) {
					return true;
				}
			}
			return false;
		}

		// Finds all the possible paths through unoccupied nodes from the given source
		private static ArrayList<Pair<Integer, Integer>> findPaths(
				GameSetup setup,
				List<Player> detectives,
				int source) {
			ArrayList<Pair<Integer, Integer>> paths = new ArrayList<>();
			for (int destination : setup.graph.adjacentNodes(source)) {
				boolean occupied = checkIfOccupied(destination, detectives);
				if (!occupied) {
					for (int destination2 : setup.graph.adjacentNodes(destination)) {
						occupied = checkIfOccupied(destination2, detectives);
						if (!occupied) {
							Pair<Integer, Integer> path = new Pair<>(destination, destination2);
							paths.add(path);
						}
					}
				}
			}
			return paths;
		}

		// Checks if player has required ticket for second move and adds it to the list
		private static void addSecondMove(int source, int destination1, int destination2, int count, Player player,
										  Ticket ticket1, Ticket ticket2, ArrayList<DoubleMove> doubleMoves) {

			if ((ticket2 == ticket1 && count >= 2) || (ticket2 != ticket1 && player.has(ticket2))) {
				DoubleMove newMove = new DoubleMove(player.piece(), source, ticket1, destination1, ticket2, destination2);
				doubleMoves.add(newMove);
			}
		}

		// Works out all possible double moves for mrX
		private static ImmutableSet<DoubleMove> makeDoubleMoves(
				GameSetup setup,
				List<Player> detectives,
				Player player,
				int source) {
			if (!player.has(Ticket.DOUBLE) || setup.rounds.size() == 1) return ImmutableSet.of();
			final var doubleMoves = new ArrayList<DoubleMove>();
			ArrayList<Pair<Integer, Integer>> paths = findPaths(setup, detectives, source);

			for (Pair<Integer, Integer> path : paths) {
				for(Transport t1 : setup.graph.edgeValueOrDefault(source,path.getKey(),ImmutableSet.of())) {
					int count = player.tickets().getOrDefault(t1.requiredTicket(), 0);
					if (count >= 1) {
						for(Transport t2 : setup.graph.edgeValueOrDefault(path.getKey(), path.getValue(), ImmutableSet.of())) {
							addSecondMove(source, path.getKey(), path.getValue(), count, player,
									t1.requiredTicket(), t2.requiredTicket(), doubleMoves);
							// First ticket is SECRET
							if (player.has(Ticket.SECRET)) {
								addSecondMove(source, path.getKey(), path.getValue(), count, player,
										Ticket.SECRET, t2.requiredTicket(), doubleMoves);
							}
						}
						// Second ticket is SECRET
						if (player.has(Ticket.SECRET)) {
							addSecondMove(source, path.getKey(), path.getValue(), count, player,
									t1.requiredTicket(), Ticket.SECRET, doubleMoves);
						}
						// Both tickets are SECRET
						if (player.tickets().getOrDefault(Ticket.SECRET, 0) >= 2) {
							addSecondMove(source, path.getKey(), path.getValue(), count, player,
									Ticket.SECRET, Ticket.SECRET, doubleMoves);
						}
					}
				}
			}

			return ImmutableSet.copyOf(doubleMoves);
		}

		@Override @Nonnull
		public GameSetup getSetup() {
			return setup;
		}

		//Returns the set of all players
		@Override @Nonnull
		public ImmutableSet<Piece> getPlayers() {
			ArrayList<Piece> pieces = new ArrayList<>();
			for (Player temp : everyone){
			 	pieces.add(temp.piece());
			 }
			return ImmutableSet.copyOf(pieces);
		}

		@Override public GameState advance(Move move) {
			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: " + move);
			// List of pairs of destinations and corresponding tickets
			List<Pair<Integer, Ticket>> destTickets = move.visit(new Visitor<>() {
				@Override
				public List<Pair<Integer, Ticket>> visit(SingleMove move) {
					Pair<Integer, Ticket> moveInfo = new Pair<>(move.destination, move.ticket);
					ArrayList<Pair<Integer, Ticket>> moveInfoList = new ArrayList<>();
					moveInfoList.add(moveInfo);
					return moveInfoList;
				}

				@Override
				public List<Pair<Integer, Ticket>> visit(DoubleMove move) {
					Pair<Integer, Ticket> move1Info = new Pair<>(move.destination1, move.ticket1);
					Pair<Integer, Ticket> move2Info = new Pair<>(move.destination2, move.ticket2);
					ArrayList<Pair<Integer, Ticket>> moveInfoList = new ArrayList<>();
					moveInfoList.add(move1Info);
					moveInfoList.add(move2Info);
					return moveInfoList;
				}
			});
			Player newMrx = mrX;
			ArrayList<Player> newDetectives;
			List<LogEntry> newLogList = new ArrayList<>(log);
			if (move.commencedBy() == MrX.MRX) {
				newMrx = mrX.use(destTickets.get(0).getValue())
						.at(destTickets.get(0).getKey());
				updateLog(newLogList, destTickets.get(0).getValue(), destTickets.get(0).getKey());

				if (destTickets.size() > 1) { // Double Move
					newMrx = newMrx.use(destTickets.get(1).getValue())
							.at(destTickets.get(1).getKey())
							.use(Ticket.DOUBLE);
					updateLog(newLogList, destTickets.get(1).getValue(), destTickets.get(1).getKey());
				}
				newDetectives = new ArrayList<>(detectives);
			}
			else {
				newDetectives = new ArrayList<>();
				for (Player oldDetective : detectives) {
					if (oldDetective.piece() != move.commencedBy()) {
						newDetectives.add(oldDetective);
					}
					else {
						Player changedDetective = oldDetective.use(destTickets.get(0).getValue())
								.at(destTickets.get(0).getKey());
						newDetectives.add(changedDetective);
						newMrx = mrX.give(move.tickets());
					}
				}
			}
			ImmutableList<LogEntry> newLog = ImmutableList.copyOf(newLogList);
			ImmutableSet<Piece> newRemaining = ImmutableSet.copyOf(updateRemaining(move));
			GameState newGameState = new MyGameState(setup, newRemaining, newLog, newMrx, newDetectives);

			return newGameState;
		}

		// Updates MrX's log given the move details
		private void updateLog(List<LogEntry> newLogList, Ticket ticket, int destination) {
			round++;
			if (setup.rounds.get(round-1) == true) {
				newLogList.add(LogEntry.reveal(ticket, destination));
			}
			else newLogList.add(LogEntry.hidden(ticket));
		}

		// Checks if the detective has any tickets
		private boolean detectiveHasAnyTicket(Player detective, boolean hasAnyTicket) {
			for (Ticket ticket : Ticket.values()) {
				if (detective.has(ticket)) hasAnyTicket = true;
			}
			return hasAnyTicket;
		}

		// Updates the set of remaining pieces given the last move
		private List<Piece> updateRemaining(Move move) {
			ArrayList<Piece> newRemainingList = new ArrayList<>();
			// If move was commenced by MrX, then detectives are remaining players in round
			if (remaining.contains(MrX.MRX)) {
				for (Player detective : detectives) {
					boolean hasAnyTicket = false;
					hasAnyTicket = detectiveHasAnyTicket(detective, hasAnyTicket);
					if (hasAnyTicket) newRemainingList.add(detective.piece());
				}
			}
			// If move was commenced by detective and more detectives remain, remove detective that just went from list of remaining
			else if (remaining.size() > 1) {
				for (Player detective : detectives) {
					boolean hasAnyTicket = false;
					hasAnyTicket = detectiveHasAnyTicket(detective, hasAnyTicket);
					if (hasAnyTicket && remaining.contains(detective.piece())) newRemainingList.add(detective.piece());
				}
				newRemainingList.remove(move.commencedBy());
			}
			// If all detectives have moved, its MrX again
			else {
				newRemainingList.add(MrX.MRX);
			}
			return newRemainingList;
		}

		//Returns location of the given detective
		@Override @Nonnull
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
			for (Player temp : detectives){
				if (temp.piece() == detective){
					return Optional.of(temp.location());
				}
			}
			return Optional.empty();
		}

		//Creates an instance of the MyTicketBoard class for the given player and returns it
		@Override @Nonnull
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			Optional<TicketBoard> optionalTicketBoard;
			for (Player temp : everyone){
				if (temp.piece() == piece){
					TicketBoard ticketBoard = new MyTicketBoard(temp.tickets());
					 optionalTicketBoard = Optional.of(ticketBoard);
					return optionalTicketBoard;
				}
			}
			optionalTicketBoard = Optional.empty();
			return optionalTicketBoard;
		}

		@Override @Nonnull
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		@Override @Nonnull
		public ImmutableSet<Piece> getWinner() {
			return winner;
		}

		@Override @Nonnull
		public ImmutableSet<Move> getAvailableMoves() {
			if (!winner.isEmpty()) return ImmutableSet.of();
			return this.moves;
		}

        //Contains the type and number of tickets a player has
		private class MyTicketBoard implements TicketBoard{
			private final ImmutableMap<Ticket, Integer> tickets;

			MyTicketBoard(ImmutableMap<Ticket, Integer> tickets){
				this.tickets = tickets;
			}

			@Override
			public int getCount(@Nonnull Ticket ticket){
				return tickets.get(ticket);
			}
		}
	}

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {
		return new MyGameState(setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
	}

}
