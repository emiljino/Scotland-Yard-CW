package uk.ac.bris.cs.scotlandyard.model;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

import java.util.ArrayList;
import java.util.List;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {

	@Nonnull @Override public Model build(GameSetup setup,
	                                      Player mrX,
	                                      ImmutableList<Player> detectives) {

		return new MyModel(setup, mrX, detectives);
	}

	private class MyModel implements Model {
		private final List<Model.Observer> observers;
		private Board.GameState gameState;

		MyModel(GameSetup setup,
				Player mrX,
				ImmutableList<Player> detectives) {
			this.observers = new ArrayList<>();
			Factory<Board.GameState> factory = new MyGameStateFactory();
			this.gameState = factory.build(setup, mrX, detectives);
		}

		@Nonnull
		public Board getCurrentBoard() {
			return gameState;
		}

		public void registerObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Attempting to register a null observer!");
			if (observers.contains(observer)) throw new IllegalArgumentException("Observer already registered!");
			this.observers.add(observer);
		}

		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("Attempting to unregister a null observer!");
			if (!observers.contains(observer)) throw new IllegalArgumentException("Observer not registered!");
			this.observers.remove(observer);
		}

		@Nonnull
		public ImmutableSet<Model.Observer> getObservers() {
			return ImmutableSet.copyOf(this.observers);
		}

		private void notifyObserver(Observer.Event event) {
			for (Observer observer : observers) {
				observer.onModelChanged(getCurrentBoard(), event);
			}
		}

		public void chooseMove(@Nonnull Move move) {
			this.gameState = this.gameState.advance(move);
			if (this.gameState.getWinner().isEmpty()) notifyObserver(Observer.Event.MOVE_MADE);
			else notifyObserver(Observer.Event.GAME_OVER);
		}

	}


}
