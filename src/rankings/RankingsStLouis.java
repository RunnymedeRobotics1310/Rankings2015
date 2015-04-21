package rankings;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class RankingsStLouis {

	enum AllianceColour { RED, BLUE }
	
	class AllianceScore {
		public final int team1, team2, team3, autoScore, autoTeam, coopScore, score;
		public final AllianceColour allianceColour;
		public final String match;
		public AllianceScore(String match, AllianceColour allianceColour, int team1, int team2, int team3, int autoScore, int autoTeam, int coopScore, int score) {
			this.match = match;
			this.allianceColour = allianceColour;
			this.team1 = team1;
			this.team2 = team2;
			this.team3 = team3;
			this.autoScore = autoScore;
			this.autoTeam  = autoTeam;
			this.coopScore = coopScore;
			this.score = score;
		}
		
		@Override
		public String toString() {
			return match + " (" + team1 + " " + team2 + " " + team3 + ") " + score; 
		}
	}
	
	class TeamScore {
		public final int team;
		public int total, matches;
		public double stackRatio, powerRatio; 
		public double autoTotal, coopTotal, stackTotal, powerTotal;
		public double average, avgAutoScore, avgCoopScore, avgStackScore, avgPowerScore;
		
		TeamScore(int team) {
			this.team = team;
		}
		
		@Override
		public String toString() {
			return "" + team + 
					"\t" + average + 
					"\t" + Math.round(avgPowerScore * 10.0) / 10.0 + 
					"\t" + Math.round(avgAutoScore * 10.0) / 10.0 +
					"\t" + Math.round(avgCoopScore * 10.0) / 10.0 +
					"\t" + Math.round(avgStackScore * 10.0) / 10.0; 
		}
	}
	
	private final String fileName;
	
	List<AllianceScore> scores = new ArrayList<AllianceScore>();
	
	Map<Integer, TeamScore> teamScores = new HashMap<Integer, TeamScore>(100);
	
	public RankingsStLouis(String fileName) {
		this.fileName = fileName;
	}
	
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage " + RankingsStLouis.class.getSimpleName() + " inputFileMatchStats");
			System.exit(-1);
		}
		
		(new RankingsStLouis(args[0])).run();
		
		System.exit(0);
	}

	public void run() {
		
		Path file = Paths.get(fileName);
		
		Charset charset = Charset.forName("US-ASCII");

		try {
			int i = 1;
			BufferedReader reader = Files.newBufferedReader(file, charset);
		    String line = null;
		    // Skip the first 3 lines (titles).
		    reader.readLine();
		    reader.readLine();
		    reader.readLine();
		    reader.readLine();
		    
		    while ((line = reader.readLine()) != null) {
		    	if (!processInputLine(i, line)) { 
		    		System.out.println("Error processing line " + line);
		    		break; 
	    		}
		    	i++;
		    }
		    
		    reader.close();
		    
		} catch (IOException x) {
		    System.err.format("IOException: %s%n", x);
		}
		
		createTeamList();
		
		calcTeamAverages();
		
		for (Entry<Integer, TeamScore> teamScoreEntry: teamScores.entrySet()) {
			TeamScore teamScore = teamScoreEntry.getValue();
			teamScore.powerRatio = teamScore.average;
			teamScore.stackRatio = teamScore.average;
		}

		// Iterate the power calculation for 100 tries
		for (int i=0; i<200; i++) {
			calcPowerScores();
		}

		//*****
		// PRINT a sorted list
		//*****
		List<TeamScore> teamScoreList = new ArrayList<TeamScore>();

		for (Entry<Integer, TeamScore> teamScoreEntry: teamScores.entrySet()) {
			teamScoreList.add(teamScoreEntry.getValue());
		}

		// Average Scores
		Collections.sort(teamScoreList, new Comparator<TeamScore>() {
			@Override
			public int compare(TeamScore o1, TeamScore o2) {
				return (int) ((o2.average-o1.average) * 10.0d);
			}
		});

		System.out.println("Average Rankings");
		for (TeamScore teamScore: teamScoreList) {
			System.out.println(teamScore);
		}

		// Power Scores
		Collections.sort(teamScoreList, new Comparator<TeamScore>() {
			@Override
			public int compare(TeamScore o1, TeamScore o2) {
				return (int) ((o2.avgPowerScore-o1.avgPowerScore) * 10.0d);
			}
		});

		System.out.println("Power Rankings");
		for (TeamScore teamScore: teamScoreList) {
			System.out.println(teamScore);
		}
		
		// Tote Stacking
		Collections.sort(teamScoreList, new Comparator<TeamScore>() {
			@Override
			public int compare(TeamScore o1, TeamScore o2) {
				return (int) ((o2.avgStackScore-o1.avgStackScore) * 10.0d);
			}
		});

		System.out.println("Stacking Rankings");
		for (TeamScore teamScore: teamScoreList) {
			System.out.println(teamScore);
		}
		
	}

	private void addTeamScore(Integer team, int score) { 

		TeamScore teamScore = teamScores.get(team); 
		if (teamScore == null) {
			teamScore = new TeamScore(team);
			teamScores.put(team, teamScore);
		}
		
		teamScore.total += score;
		teamScore.matches++;
	}

	private void calcPowerScores() {
		
		for (AllianceScore score: scores) {

			TeamScore teamScore1 = teamScores.get(score.team1);
			TeamScore teamScore2 = teamScores.get(score.team2);
			TeamScore teamScore3 = teamScores.get(score.team3);
			
			// Power Score
			double powerTotalRatio = teamScore1.powerRatio + teamScore2.powerRatio + teamScore3.powerRatio;
			
			// Allocate the match score to each team based on the total.
			teamScore1.powerTotal += score.score * teamScore1.powerRatio / powerTotalRatio;
			teamScore2.powerTotal += score.score * teamScore2.powerRatio / powerTotalRatio;
			teamScore3.powerTotal += score.score * teamScore3.powerRatio / powerTotalRatio;

			// Auto Score
			// Allocate the auto score to either the team that scored the points or all teams if there
			// is no team number for the auto score.
			if (score.autoScore != 0) {
				if (score.autoTeam != 0) {
					if        (score.autoTeam == teamScore1.team) {
						teamScore1.autoTotal += score.autoScore;
					} else if (score.autoTeam == teamScore2.team) {
						teamScore2.autoTotal += score.autoScore;
					} else if (score.autoTeam == teamScore3.team) {
						teamScore3.autoTotal += score.autoScore;
					} else    {
						teamScore1.autoTotal += score.autoScore / 3;
						teamScore2.autoTotal += score.autoScore / 3;
						teamScore3.autoTotal += score.autoScore / 3;
					}
				} else {
					teamScore1.autoTotal += score.autoScore / 3;
					teamScore2.autoTotal += score.autoScore / 3;
					teamScore3.autoTotal += score.autoScore / 3;
				} 
			}

			// Coop Score
			if (score.coopScore != 0) {
				teamScore1.coopTotal += score.coopScore / 3;
				teamScore2.coopTotal += score.coopScore / 3;
				teamScore3.coopTotal += score.coopScore / 3;
			}
			
			// stacking Score
			double stackTotalRatio = teamScore1.stackRatio + teamScore2.stackRatio + teamScore3.stackRatio;
			
			double stackScore = score.score - score.autoScore - score.coopScore;
			
			// Allocate the match score to each team based on the total.
			teamScore1.stackTotal += stackScore * teamScore1.stackRatio / stackTotalRatio;
			teamScore2.stackTotal += stackScore * teamScore2.stackRatio / stackTotalRatio;
			teamScore3.stackTotal += stackScore * teamScore3.stackRatio / stackTotalRatio;


		}
		
		for (Entry<Integer, TeamScore> teamScoreEntry: teamScores.entrySet()) {
			
			TeamScore teamScore = teamScoreEntry.getValue();
			
			teamScore.avgPowerScore = (teamScore.powerTotal * 1.0d) / (teamScore.matches * 1.0d);
			teamScore.powerRatio = teamScore.avgPowerScore;
			
			teamScore.avgAutoScore = (teamScore.autoTotal * 1.0d) / (teamScore.matches * 1.0d);
			
			teamScore.avgCoopScore = (teamScore.coopTotal * 1.0d) / (teamScore.matches * 1.0d);
			
			teamScore.avgStackScore = (teamScore.stackTotal * 1.0d) / (teamScore.matches * 1.0d);
			teamScore.stackRatio = teamScore.avgStackScore;
			
			// Clear the totals
			teamScore.autoTotal = 0;
			teamScore.coopTotal = 0;
			teamScore.stackTotal = 0;
			teamScore.powerTotal = 0;
			
			continue;
		}
		
	}
	
	private void calcTeamAverages() {

		for (Entry<Integer,TeamScore> teamScoreEntry: teamScores.entrySet()) {
			TeamScore teamScore = teamScoreEntry.getValue();
			teamScore.average = Math.round((teamScore.total * 1.0d) / (teamScore.matches * 1.0d) * 10) / 10.0d;
		}
	}

	private void createTeamList() {
	
		for (AllianceScore score: scores) {

			addTeamScore(score.team1, score.score);
			addTeamScore(score.team2, score.score);
			addTeamScore(score.team3, score.score);
		}
	}

	private Integer getInt(String value) {
		if (value.trim().isEmpty()) { return new Integer(0); }
		return (int) (Math.round(new Double(value)));
	}
	
	private boolean processInputLine(int lineNumber, String lineString) {
		
		String [] fields = lineString.split(",");

		for (String field: fields) {
			field = field.trim();
		}
		
		if (fields.length < 17) { return false; }

		// If there is no score for RED or BLUE, then assume the match has not been played
		// and stop
		if (getInt(fields[8]) == 0 && getInt(fields[16]) == 0) { return false; }
		
		scores.add(new AllianceScore(
				fields[0], 
				AllianceColour.RED,
				getInt(fields[2]),
				getInt(fields[3]),
				getInt(fields[4]),
				getInt(fields[5]),
				getInt(fields[6]),
				getInt(fields[7]),
				getInt(fields[8])
				));
		
		scores.add(new AllianceScore(
				fields[0], 
				AllianceColour.BLUE, 
				getInt(fields[10]),
				getInt(fields[11]),
				getInt(fields[12]),
				getInt(fields[13]),
				getInt(fields[14]),
				getInt(fields[15]),
				getInt(fields[16])
				));
		
		return true;
		
	}
	
	

}
