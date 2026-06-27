package com.mindmate.service;

import com.mindmate.model.Analysis;
import com.mindmate.model.ChatReply;
import com.mindmate.model.ChatRequest;
import com.mindmate.model.Dashboard;
import com.mindmate.model.JournalEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WellnessService {
    private final List<JournalEntry> entries = new ArrayList<>();
    private final Map<String, List<String>> lexicon = new LinkedHashMap<>();

    public WellnessService() {
        lexicon.put("time pressure", List.of("late", "deadline", "behind", "time", "rush", "backlog", "syllabus"));
        lexicon.put("mock test anxiety", List.of("mock", "score", "rank", "percentile", "marks", "test series"));
        lexicon.put("comparison", List.of("others", "friend", "classmate", "compare", "rank", "topper"));
        lexicon.put("family expectations", List.of("parents", "family", "expect", "pressure", "disappoint"));
        lexicon.put("concept confusion", List.of("stuck", "confused", "chapter", "topic", "concept", "doubt"));
        lexicon.put("body fatigue", List.of("tired", "headache", "sleepy", "exhausted", "burnout", "drained"));
        lexicon.put("self-doubt", List.of("fail", "can't", "cannot", "hopeless", "useless", "not enough"));
    }

    public synchronized List<JournalEntry> getEntries() {
        return entries.stream()
                .sorted(Comparator.comparing(entry -> entry.createdAt, Comparator.reverseOrder()))
                .toList();
    }

    public synchronized JournalEntry addEntry(JournalEntry entry) {
        entry.id = UUID.randomUUID().toString();
        entry.createdAt = Instant.now().toString();
        entry.journal = clean(entry.journal);
        entry.examTrack = clean(entry.examTrack).isBlank() ? "Exam prep" : clean(entry.examTrack);
        entry.mood = clamp(entry.mood, 1, 10);
        entry.stress = clamp(entry.stress, 1, 10);
        entry.energy = clamp(entry.energy, 1, 10);
        entry.sleepHours = Math.max(0, Math.min(14, entry.sleepHours));
        entry.analysis = analyze(entry);
        entries.add(entry);
        return entry;
    }

    public synchronized Dashboard buildDashboard() {
        Dashboard dashboard = new Dashboard();
        dashboard.entriesCount = entries.size();
        if (entries.isEmpty()) {
            dashboard.patterns = List.of("Start with one honest journal entry to unlock personalized patterns.");
            dashboard.recommendedPlan = List
                    .of("Write for two minutes tonight: what drained you, what helped, and one kind next step.");
            return dashboard;
        }

        dashboard.averageMood = average(entries.stream().map(entry -> entry.mood).toList());
        dashboard.averageStress = average(entries.stream().map(entry -> entry.stress).toList());
        dashboard.averageSleep = entries.stream().mapToDouble(entry -> entry.sleepHours).average().orElse(0);
        dashboard.topTriggers = topItems(entries.stream().flatMap(entry -> entry.analysis.triggers.stream()).toList(),
                5);
        dashboard.recurringEmotions = topItems(
                entries.stream().flatMap(entry -> entry.analysis.emotions.stream()).toList(), 5);
        dashboard.patterns = inferPatterns();
        dashboard.recommendedPlan = buildPlan(dashboard);
        dashboard.trend = entries.stream()
                .sorted(Comparator.comparing(entry -> entry.createdAt))
                .map(entry -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.parse(entry.createdAt)));
                    point.put("mood", entry.mood);
                    point.put("stress", entry.stress);
                    point.put("sleep", entry.sleepHours);
                    return point;
                }).toList();
        return dashboard;
    }

    public synchronized ChatReply replyTo(ChatRequest request) {
        ChatReply reply = new ChatReply();
        String message = clean(request.message).toLowerCase(Locale.ENGLISH);
        JournalEntry latest = entries.isEmpty() ? null : entries.get(entries.size() - 1);

        if (containsAny(message, List.of("suicide", "self harm", "kill myself", "end it", "hurt myself"))) {
            reply.crisisSupport = true;
            reply.reply = "I am really glad you told me. This sounds urgent, and you deserve real support right now. Please contact a trusted adult nearby, your local emergency number, or a mental health crisis helpline immediately. If you can, move close to another person and put distance between yourself and anything you could use to harm yourself.";
            reply.actions = List.of("Tell one trusted person exactly what you wrote here",
                    "Call emergency services or a local crisis helpline",
                    "Keep the next five minutes simple: breathe, sit somewhere visible, and stay connected");
            return reply;
        }

        if (message.contains("mock") || message.contains("score") || message.contains("rank")) {
            reply.reply = "A score can feel like a verdict, but it is really diagnostic data. Let us shrink it: name one topic that cost the most marks, revise it for 25 minutes, then do five questions untimed before you judge yourself again.";
            reply.actions = List.of("Circle one fixable topic", "Do a 4-6 breathing cycle for two minutes",
                    "Plan one short correction block instead of a full-day overhaul");
            return reply;
        }

        if (message.contains("tired") || message.contains("burnout") || message.contains("exhausted")) {
            reply.reply = "Your mind may be asking for recovery, not more pressure. A high-stakes exam is a long game; tonight, protect sleep and do a low-friction review so you keep trust with yourself without pushing past empty.";
            reply.actions = List.of("Take a screen-free 15 minute reset", "Revise flashcards or formulas only",
                    "Set a sleep cutoff and keep it");
            return reply;
        }

        if (latest != null && latest.analysis.riskScore >= 7) {
            reply.reply = "I noticed your recent log carried heavy stress signals. Let us make this moment smaller: unclench your jaw, slow your exhale, and pick the next action that is useful but not heroic.";
            reply.actions = List.of("Try 5-4-3-2-1 grounding", "Text a friend or mentor a one-line check-in",
                    "Choose one 20 minute task");
            return reply;
        }

        reply.reply = "I am with you. What you wrote sounds like exam pressure trying to become identity pressure. For today, separate the student from the score: you are a person practicing under pressure, and the next useful step can be small.";
        reply.actions = List.of("Write one worry and one controllable action", "Do three slow exhales before studying",
                "End the next study block by noting one thing you understood better");
        return reply;
    }

    private Analysis analyze(JournalEntry entry) {
        Analysis analysis = new Analysis();
        String text = entry.journal.toLowerCase(Locale.ENGLISH);
        analysis.triggers = detectTriggers(text);
        analysis.emotions = detectEmotions(text, entry);
        analysis.riskScore = Math.min(10, entry.stress + (entry.mood <= 3 ? 2 : 0) + (entry.sleepHours < 5 ? 1 : 0));
        analysis.needsHumanSupport = analysis.riskScore >= 9
                || containsAny(text, List.of("panic", "hopeless", "can't go on", "self harm"));
        analysis.tone = analysis.riskScore >= 8 ? "overloaded" : entry.stress >= 6 ? "strained" : "reflective";
        analysis.hiddenPatterns = inferEntryPatterns(entry, analysis);
        analysis.copingStrategies = strategiesFor(analysis, entry);
        analysis.summary = summarize(entry, analysis);
        analysis.encouragement = encouragementFor(entry, analysis);
        return analysis;
    }

    private List<String> detectTriggers(String text) {
        List<String> triggers = new ArrayList<>();
        for (Map.Entry<String, List<String>> item : lexicon.entrySet()) {
            if (containsAny(text, item.getValue())) {
                triggers.add(item.getKey());
            }
        }
        if (triggers.isEmpty()) {
            triggers.add("general exam load");
        }
        return triggers;
    }

    private List<String> detectEmotions(String text, JournalEntry entry) {
        List<String> emotions = new ArrayList<>();
        if (containsAny(text, List.of("anxious", "panic", "scared", "worried", "nervous")))
            emotions.add("anxiety");
        if (containsAny(text, List.of("sad", "cry", "low", "alone", "lonely")))
            emotions.add("sadness");
        if (containsAny(text, List.of("angry", "irritated", "frustrated")))
            emotions.add("frustration");
        if (containsAny(text, List.of("guilty", "wasted", "regret")))
            emotions.add("guilt");
        if (containsAny(text, List.of("hope", "better", "proud", "calm")))
            emotions.add("hope");
        if (entry.energy <= 3 || containsAny(text, List.of("tired", "exhausted", "drained")))
            emotions.add("fatigue");
        if (emotions.isEmpty())
            emotions.add(entry.mood >= 6 ? "steady focus" : "unease");
        return emotions;
    }

    private List<String> inferEntryPatterns(JournalEntry entry, Analysis analysis) {
        List<String> patterns = new ArrayList<>();
        if (entry.sleepHours < 6 && entry.stress >= 7) {
            patterns.add("Low sleep and high stress are showing up together.");
        }
        if (analysis.triggers.contains("mock test anxiety") && analysis.emotions.contains("self-doubt")) {
            patterns.add("Performance feedback may be turning into self-worth pressure.");
        }
        if (analysis.triggers.contains("comparison")) {
            patterns.add("Comparison language is pulling attention away from controllable practice.");
        }
        if (patterns.isEmpty()) {
            patterns.add("Today's stress looks manageable with one focused recovery habit.");
        }
        return patterns;
    }

    private List<String> strategiesFor(Analysis analysis, JournalEntry entry) {
        List<String> strategies = new ArrayList<>();
        if (analysis.triggers.contains("time pressure")) {
            strategies.add("Make a two-column plan: urgent today, important this week.");
        }
        if (analysis.triggers.contains("mock test anxiety")) {
            strategies.add("Convert the mock into three error buckets: concept, speed, attention.");
        }
        if (entry.sleepHours < 6 || analysis.emotions.contains("fatigue")) {
            strategies.add("Use a recovery block before another hard study block.");
        }
        if (analysis.riskScore >= 8) {
            strategies.add("Try 5-4-3-2-1 grounding, then contact a trusted person if the feeling stays intense.");
        }
        strategies.add("Take three longer exhales before opening the next chapter.");
        return strategies.stream().distinct().toList();
    }

    private String summarize(JournalEntry entry, Analysis analysis) {
        return "Your log points to " + String.join(", ", analysis.emotions)
                + " around " + String.join(", ", analysis.triggers)
                + ". Stress is at " + entry.stress + "/10, so today's support should stay practical and gentle.";
    }

    private String encouragementFor(JournalEntry entry, Analysis analysis) {
        if (analysis.riskScore >= 8) {
            return "You do not have to solve the whole exam journey tonight. Stabilizing yourself is productive work.";
        }
        if (entry.mood >= 7) {
            return "There is momentum here. Protect it with steady routines, not extra pressure.";
        }
        return "One hard day is information, not a final result. Small honest steps still count.";
    }

    private List<String> inferPatterns() {
        List<String> patterns = new ArrayList<>();
        long lowSleepHighStress = entries.stream().filter(entry -> entry.sleepHours < 6 && entry.stress >= 7).count();
        long mockAnxiety = entries.stream().filter(entry -> entry.analysis.triggers.contains("mock test anxiety"))
                .count();
        long comparison = entries.stream().filter(entry -> entry.analysis.triggers.contains("comparison")).count();
        if (lowSleepHighStress >= 2)
            patterns.add("Stress spikes are repeatedly appearing on lower-sleep days.");
        if (mockAnxiety >= 2)
            patterns.add("Mock-test feedback is a recurring emotional trigger.");
        if (comparison >= 2)
            patterns.add("Comparison is becoming a repeated drain on confidence.");
        if (patterns.isEmpty())
            patterns.add("Keep logging for a few days to reveal stronger emotional patterns.");
        return patterns;
    }

    private List<String> buildPlan(Dashboard dashboard) {
        List<String> plan = new ArrayList<>();
        if (dashboard.averageSleep < 6.5)
            plan.add("Set a fixed sleep cutoff for the next three nights.");
        if (dashboard.averageStress >= 7)
            plan.add("Use a two-minute grounding exercise before each study block.");
        if (dashboard.topTriggers.contains("mock test anxiety"))
            plan.add("After each mock, write one correction action before checking rank discussions.");
        if (dashboard.topTriggers.contains("comparison"))
            plan.add("Mute one comparison source during focused revision windows.");
        plan.add("End the day with one line: what I handled, what I will adjust, what I can release.");
        return plan.stream().distinct().toList();
    }

    private List<String> topItems(List<String> items, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (String item : items)
            counts.merge(item, 1, Integer::sum);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double average(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
