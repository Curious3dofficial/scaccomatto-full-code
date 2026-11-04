package com.scaccomatto.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EloSystem {
    record Rule(String name, int minElo, int maxElo, int win, int draw, int lose) {
        int deltaFor(String result) {
            return switch (result) {
                case "win" -> win;
                case "draw" -> draw;
                case "loss" -> lose;
                default -> throw new IllegalArgumentException("Unsupported result: " + result);
            };
        }
    }

    private static final Pattern RULE_PATTERN = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                    + "\"minElo\"\\s*:\\s*(-?\\d+)\\s*,\\s*"
                    + "\"maxElo\"\\s*:\\s*(-?\\d+)\\s*,\\s*"
                    + "\"win\"\\s*:\\s*(-?\\d+)\\s*,\\s*"
                    + "\"draw\"\\s*:\\s*(-?\\d+)\\s*,\\s*"
                    + "\"lose\"\\s*:\\s*(-?\\d+)\\s*\\}",
            Pattern.DOTALL);

    /** One row of the rating-change table: changes for a given rating difference. */
    record Step(int ratingDiff, double settledWin, double settledDraw, double settledLoss,
                double volatileWin, double volatileDraw, double volatileLoss) {
        double deltaFor(String result, boolean volatileRating) {
            return switch (result) {
                case "win" -> volatileRating ? volatileWin : settledWin;
                case "draw" -> volatileRating ? volatileDraw : settledDraw;
                case "loss" -> volatileRating ? volatileLoss : settledLoss;
                default -> throw new IllegalArgumentException("Unsupported result: " + result);
            };
        }
    }

    private static final String NUM = "(-?\\d+(?:\\.\\d+)?)";
    private static final Pattern STEP_PATTERN = Pattern.compile(
            "\"rating_diff\"\\s*:\\s*(-?\\d+).*?"
                    + "\"settled_K16\"\\s*:\\s*\\{\\s*\"win\"\\s*:\\s*" + NUM
                    + "\\s*,\\s*\"draw\"\\s*:\\s*" + NUM
                    + "\\s*,\\s*\"loss\"\\s*:\\s*" + NUM + "\\s*\\}.*?"
                    + "\"volatile_K32\"\\s*:\\s*\\{\\s*\"win\"\\s*:\\s*" + NUM
                    + "\\s*,\\s*\"draw\"\\s*:\\s*" + NUM
                    + "\\s*,\\s*\"loss\"\\s*:\\s*" + NUM + "\\s*\\}",
            Pattern.DOTALL);

    /** Players with fewer finished games than this move by the volatile (K32) column. */
    static final int VOLATILE_GAMES = 30;

    private final List<Rule> rules;      // rank names (Wood, Bronze...) and the rating floor/ceiling
    private final List<Step> steps;      // rating change by rating difference
    private final int minRating;
    private final int maxRating;

    private EloSystem(List<Rule> rules, List<Step> steps) {
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("ELO rules must contain at least one tier.");
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("The ELO table must contain at least one row.");
        }
        this.rules = List.copyOf(rules);
        this.steps = List.copyOf(steps);
        this.minRating = this.rules.stream().mapToInt(Rule::minElo).min().orElse(0);
        this.maxRating = this.rules.stream().mapToInt(Rule::maxElo).max().orElse(3500);
    }

    /**
     * @param rulesPath the rank tiers (names and rating ranges)
     * @param tablePath the rating-change table (by rating difference, settled K16 / volatile K32)
     */
    static EloSystem load(Path rulesPath, Path tablePath) throws IOException {
        String source = Files.readString(rulesPath);
        Matcher matcher = RULE_PATTERN.matcher(source);
        List<Rule> rules = new ArrayList<>();
        while (matcher.find()) {
            rules.add(new Rule(
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)),
                    Integer.parseInt(matcher.group(6))));
        }
        rules.sort(Comparator.comparingInt(Rule::minElo));

        String table = Files.readString(tablePath);
        // only the "table" array holds rows; skip anything before it
        int start = table.indexOf("\"table\"");
        Matcher rows = STEP_PATTERN.matcher(start >= 0 ? table.substring(start) : table);
        List<Step> steps = new ArrayList<>();
        while (rows.find()) {
            steps.add(new Step(
                    Integer.parseInt(rows.group(1)),
                    Double.parseDouble(rows.group(2)),
                    Double.parseDouble(rows.group(3)),
                    Double.parseDouble(rows.group(4)),
                    Double.parseDouble(rows.group(5)),
                    Double.parseDouble(rows.group(6)),
                    Double.parseDouble(rows.group(7))));
        }
        steps.sort(Comparator.comparingInt(Step::ratingDiff));
        return new EloSystem(rules, steps);
    }

    /**
     * The rating change for one game, read from the table: the row nearest to
     * (your rating - opponent's rating), clamped to the table's ends. With no known
     * opponent rating the game counts as even (difference 0).
     *
     * @param gamesPlayed finished games before this one; fewer than
     *                    {@link #VOLATILE_GAMES} uses the volatile (K32) column
     */
    RatingChange apply(double currentRating, Double opponentRating, String result, int gamesPlayed) {
        double diff = opponentRating == null ? 0 : currentRating - opponentRating;
        Step step = stepFor(diff);
        boolean volatileRating = gamesPlayed < VOLATILE_GAMES;
        double delta = step.deltaFor(result, volatileRating);
        // one decimal, like the table, so tiny floating-point errors never pile up
        double nextRating = Math.round((currentRating + delta) * 10.0) / 10.0;
        nextRating = Math.max(minRating, Math.min(maxRating, nextRating));
        double change = Math.round((nextRating - currentRating) * 10.0) / 10.0;
        return new RatingChange(
                currentRating,
                nextRating,
                change,
                ruleFor(currentRating).name(),
                ruleFor(nextRating).name());
    }

    private Step stepFor(double diff) {
        Step best = steps.get(0);
        for (Step s : steps) {
            if (Math.abs(s.ratingDiff() - diff) < Math.abs(best.ratingDiff() - diff)) best = s;
        }
        return best;
    }

    private Rule ruleFor(double exactRating) {
        long rating = Math.round(exactRating);   // ranks go by the rating as shown
        for (Rule rule : rules) {
            if (rating >= rule.minElo() && rating <= rule.maxElo()) return rule;
        }
        return rating < minRating ? rules.get(0) : rules.get(rules.size() - 1);
    }

    record RatingChange(
            double previousRating,
            double newRating,
            double ratingDelta,
            String previousRank,
            String newRank) {
    }
}
