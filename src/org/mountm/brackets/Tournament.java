package org.mountm.brackets;

public class Tournament {
	
	public static final int NUM_ROUNDS = 6;
	public static final int NUM_TEAMS = (int) Math.pow(2, NUM_ROUNDS);
	
	private static int[] gamesPerRound = new int[NUM_ROUNDS];
	private static int[] pointsPerGame = new int[NUM_ROUNDS];
	private static long[][]teamRoundMasks = new long[NUM_TEAMS][NUM_ROUNDS];
	private static long[][]teamRoundValues = new long[NUM_TEAMS][NUM_ROUNDS];
	
	public static long[][] getMasks() {
		return teamRoundMasks;
	}
	
	public static long[][] getValues() {
		return teamRoundValues;
	}
	
	/*
	 *  Bits represent the result of individual games.
	 *  LSB represents the first game.
	 *  0 means the top team in that matchup won.
	 *  1 means the bottom team won
	 */
	private long results;
	/*
	 * Bits represent whether a certain game has been played
	 * 0 means the game has not been played
	 * 1 means the game has been played
	 */
	private long gamesMask;
	
	public Tournament() {
		results = 0L;
		gamesMask = 0L;
		generateScoringData();
	}
	
	public void setResults(long results, long gamesMask) {
		this.results = results;
		this.gamesMask = gamesMask;
	}
	
	public void setResult(boolean result, int position) {
		if (result) {
			results |= 1L << position;
		}
		gamesMask |= 1L << position;
	}
	
	public long getResults() {
		return results;
	}
	public long getGamesMask() {
		return gamesMask;
	}

	public int scoreBracket(Bracket bracket) {
		int score = 0;
		long picks = bracket.getPicks();
		for (int team = 0; team < NUM_TEAMS; team++) {
			for (int round = NUM_ROUNDS - 1; round >= 0; round--) {
				// check if all the games in this mask have been played and results match this team winning
				if (((gamesMask & teamRoundMasks[team][round]) == teamRoundMasks[team][round]) && ((results & teamRoundMasks[team][round]) == teamRoundValues[team][round])) {
					// Only add to score if predictions match mask values
					if ((picks & teamRoundMasks[team][round]) == teamRoundValues[team][round]) {
						// If you picked a team to win in a given round, you must have picked them in prev rounds also
						for (int i = 0; i <= round; i++) {
							score += pointsPerGame[i];
						}
						// no need to check other masks and values for that team
						break;
					}
				}
			}
		}
		return score;
	}
	public int scoreFinishedBracket(Bracket bracket) {
		int score = 0;
		long picks = bracket.getPicks();
		for (int team = 0; team < NUM_TEAMS; team++) {
			for (int round = NUM_ROUNDS - 1; round >= 0; round--) {
				// check if results match this team winning
				if ((results & teamRoundMasks[team][round]) == teamRoundValues[team][round]) {
					// Only add to score if predictions match mask values
					if ((picks & teamRoundMasks[team][round]) == teamRoundValues[team][round]) {
						// If you picked a team to win in a given round, you must have picked them in prev rounds also
						for (int i = 0; i <= round; i++) {
							score += pointsPerGame[i];
						}
						// no need to check other masks and values for that team
						break;
					}
				}
			}
		}
		return score;
	}

	
	private void generateScoringData() {
		for (int round = 0; round < NUM_ROUNDS; round++) {
			gamesPerRound[round] = (int) Math.pow(2, (NUM_ROUNDS - 1) - round);
			pointsPerGame[round] = (int) Math.pow(2, round);
		}
		for (int team = 0; team < NUM_TEAMS; team++) {
			for (int round = 0; round < NUM_ROUNDS; round++) {
				teamRoundMasks[team][round] = generateMask(team, round);
				teamRoundValues[team][round] = generateValue(team, round);
			}
		}
	}

	private long generateMask(int teamPos, int round) {
		long mask = 1L << (teamPos / 2);
		int shift = 0;
		for (int i = 0; i < round; i++) {
			teamPos /= 2;
			shift += gamesPerRound[i];
			mask |= 1L << (shift + (teamPos / 2));
		}
		return mask;
	}
	
	private long generateValue(int teamPos, int round) {
		long val = ((long) teamPos % 2) << (teamPos / 2);
		int shift = 0;
		for (int i = 0; i < round; i++) {
			teamPos /= 2;
			shift += gamesPerRound[i];
			val |= ((long) teamPos % 2) << (shift + (teamPos / 2));
		}
		return val;
	}
}
