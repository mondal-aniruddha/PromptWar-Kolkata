import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
    Activity,
    Brain,
    HeartPulse,
    MessageCircle,
    Moon,
    RefreshCcw,
    Send,
    ShieldCheck,
    Sparkles,
    Target,
} from "lucide-react";
import { createEntry, getEntries, getInsights, sendChat } from "./api";
import "./styles.css";

const initialForm = {
    examTrack: "JEE",
    mood: 6,
    stress: 6,
    energy: 5,
    sleepHours: 6.5,
    journal:
        "I felt tense after a mock test and kept comparing my score with friends. I am worried I am falling behind, but revising mistakes helped a little.",
};

function App() {
    const [entries, setEntries] = useState([]);
    const [dashboard, setDashboard] = useState(null);
    const [form, setForm] = useState(initialForm);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const [chatInput, setChatInput] = useState("I feel anxious about my next mock test");
    const [messages, setMessages] = useState([
        {
            role: "assistant",
            text: "Hi, I am here with you. Share what today felt like, and I will help turn it into a small, kind next step.",
            actions: ["Log today's mood", "Name one stress trigger", "Take one slower exhale"],
        },
    ]);

    async function loadData() {
        try {
            setError("");
            const [entryData, insightData] = await Promise.all([getEntries(), getInsights()]);
            setEntries(entryData);
            setDashboard(insightData);
        } catch (requestError) {
            setError("Backend is not reachable. Start the Java API on port 9090, then refresh.");
        }
    }

    useEffect(() => {
        loadData();
    }, []);

    const latest = entries[0];

    const trendMax = useMemo(() => {
        const values = dashboard?.trend?.flatMap((point) => [point.mood, point.stress]) || [10];
        return Math.max(10, ...values);
    }, [dashboard]);

    async function submitEntry(event) {
        event.preventDefault();
        setSaving(true);
        try {
            const saved = await createEntry(form);
            setForm({ ...initialForm, journal: "" });
            await loadData();
            setMessages((current) => [
                ...current,
                {
                    role: "assistant",
                    text: saved.analysis.summary,
                    actions: saved.analysis.copingStrategies,
                    important: saved.analysis.needsHumanSupport,
                },
            ]);
        } catch (requestError) {
            setError("Could not save the journal entry. Check the backend terminal.");
        } finally {
            setSaving(false);
        }
    }

    async function submitChat(event) {
        event.preventDefault();
        const trimmed = chatInput.trim();
        if (!trimmed) return;
        setMessages((current) => [...current, { role: "user", text: trimmed }]);
        setChatInput("");
        try {
            const reply = await sendChat(trimmed, latest?.id || "");
            setMessages((current) => [
                ...current,
                { role: "assistant", text: reply.reply, actions: reply.actions, important: reply.crisisSupport },
            ]);
        } catch (requestError) {
            setMessages((current) => [
                ...current,
                { role: "assistant", text: "I could not reach the backend, but I am still here. Try one slow exhale and refresh the app once the API is running." },
            ]);
        }
    }

    return (
        <main className="app-shell">
            <section className="topbar">
                <div>
                    <p className="eyebrow">Exam wellness companion</p>
                    <h1>MindMate</h1>
                </div>
                <button className="icon-button" onClick={loadData} title="Refresh insights">
                    <RefreshCcw size={18} />
                </button>
            </section>

            {error && <div className="alert">{error}</div>}

            <section className="hero-panel">
                <div className="hero-copy">
                    <p className="eyebrow">For NEET, JEE, CUET, CAT, GATE, UPSC and board exams</p>
                    <h2>Turn honest journaling into calm, specific next steps.</h2>
                    <p>
                        MindMate reads mood logs and open-ended reflections to detect stress triggers,
                        emotional patterns, and recovery actions that ordinary trackers miss.
                    </p>
                </div>
                <div className="pulse-visual" aria-label="Wellness signal visualization">
                    <span style={{ height: "36%" }} />
                    <span style={{ height: "64%" }} />
                    <span style={{ height: "46%" }} />
                    <span style={{ height: "78%" }} />
                    <span style={{ height: "54%" }} />
                    <span style={{ height: "42%" }} />
                </div>
            </section>

            <section className="layout-grid">
                <JournalForm form={form} setForm={setForm} submitEntry={submitEntry} saving={saving} />
                <CompanionChat
                    messages={messages}
                    chatInput={chatInput}
                    setChatInput={setChatInput}
                    submitChat={submitChat}
                />
            </section>

            <section className="insights-grid">
                <MetricCard icon={<HeartPulse />} label="Average mood" value={formatNumber(dashboard?.averageMood)} suffix="/10" />
                <MetricCard icon={<Activity />} label="Average stress" value={formatNumber(dashboard?.averageStress)} suffix="/10" />
                <MetricCard icon={<Moon />} label="Average sleep" value={formatNumber(dashboard?.averageSleep)} suffix="h" />
                <MetricCard icon={<Target />} label="Journal logs" value={dashboard?.entriesCount || 0} suffix="" />
            </section>

            <section className="bottom-grid">
                <Panel title="Hidden Patterns" icon={<Brain size={18} />}>
                    <List items={dashboard?.patterns || []} />
                </Panel>

                <Panel title="Adaptive Plan" icon={<ShieldCheck size={18} />}>
                    <List items={dashboard?.recommendedPlan || []} />
                </Panel>

                <Panel title="Mood And Stress Trend" icon={<Sparkles size={18} />}>
                    <div className="trend-chart">
                        {(dashboard?.trend || []).map((point, index) => (
                            <div className="trend-point" key={`${point.date}-${index}`}>
                                <div className="bars">
                                    <span className="mood" style={{ height: `${(point.mood / trendMax) * 100}%` }} />
                                    <span className="stress" style={{ height: `${(point.stress / trendMax) * 100}%` }} />
                                </div>
                                <small>{point.date}</small>
                            </div>
                        ))}
                        {(!dashboard?.trend || dashboard.trend.length === 0) && <p className="muted">Your chart appears after the first log.</p>}
                    </div>
                </Panel>

                <Panel title="Latest AI Reflection" icon={<MessageCircle size={18} />}>
                    {latest ? (
                        <div className="reflection">
                            <p>{latest.analysis.summary}</p>
                            <p className="encouragement">{latest.analysis.encouragement}</p>
                            <div className="chips">
                                {latest.analysis.triggers.map((trigger) => (
                                    <span key={trigger}>{trigger}</span>
                                ))}
                            </div>
                        </div>
                    ) : (
                        <p className="muted">No reflections yet. Add a journal entry to begin.</p>
                    )}
                </Panel>
            </section>
        </main>
    );
}

function JournalForm({ form, setForm, submitEntry, saving }) {
    function update(field, value) {
        setForm((current) => ({ ...current, [field]: value }));
    }

    return (
        <form className="tool-panel journal-panel" onSubmit={submitEntry}>
            <div className="panel-heading">
                <div>
                    <p className="eyebrow">Daily log</p>
                    <h2>What did today feel like?</h2>
                </div>
                <Brain size={22} />
            </div>

            <label>
                Exam track
                <select value={form.examTrack} onChange={(event) => update("examTrack", event.target.value)}>
                    {["JEE", "NEET", "CUET", "CAT", "GATE", "UPSC", "Board exams"].map((exam) => (
                        <option key={exam}>{exam}</option>
                    ))}
                </select>
            </label>

            <div className="slider-grid">
                <Slider label="Mood" value={form.mood} onChange={(value) => update("mood", value)} />
                <Slider label="Stress" value={form.stress} onChange={(value) => update("stress", value)} />
                <Slider label="Energy" value={form.energy} onChange={(value) => update("energy", value)} />
                <label>
                    Sleep hours
                    <input
                        type="number"
                        min="0"
                        max="14"
                        step="0.5"
                        value={form.sleepHours}
                        onChange={(event) => update("sleepHours", Number(event.target.value))}
                    />
                </label>
            </div>

            <label>
                Journal
                <textarea
                    value={form.journal}
                    onChange={(event) => update("journal", event.target.value)}
                    placeholder="Write freely: what happened, what felt heavy, what helped, and what you need next."
                    rows={8}
                />
            </label>

            <button className="primary-button" disabled={saving}>
                <Sparkles size={18} />
                {saving ? "Analyzing" : "Analyze my day"}
            </button>
        </form>
    );
}

function CompanionChat({ messages, chatInput, setChatInput, submitChat }) {
    return (
        <section className="tool-panel chat-panel">
            <div className="panel-heading">
                <div>
                    <p className="eyebrow">Companion chat</p>
                    <h2>Contextual support</h2>
                </div>
                <MessageCircle size={22} />
            </div>

            <div className="messages">
                {messages.map((message, index) => (
                    <article className={`message ${message.role} ${message.important ? "important" : ""}`} key={`${message.role}-${index}`}>
                        <p>{message.text}</p>
                        {message.actions?.length > 0 && (
                            <div className="action-list">
                                {message.actions.map((action) => (
                                    <span key={action}>{action}</span>
                                ))}
                            </div>
                        )}
                    </article>
                ))}
            </div>

            <form className="chat-input" onSubmit={submitChat}>
                <input
                    value={chatInput}
                    onChange={(event) => setChatInput(event.target.value)}
                    placeholder="Tell MindMate what feels difficult right now"
                />
                <button className="icon-button send-button" title="Send message">
                    <Send size={18} />
                </button>
            </form>
        </section>
    );
}

function Slider({ label, value, onChange }) {
    return (
        <label>
            <span className="slider-label">
                {label}
                <strong>{value}/10</strong>
            </span>
            <input type="range" min="1" max="10" value={value} onChange={(event) => onChange(Number(event.target.value))} />
        </label>
    );
}

function MetricCard({ icon, label, value, suffix }) {
    return (
        <article className="metric-card">
            <div className="metric-icon">{icon}</div>
            <p>{label}</p>
            <strong>
                {value}
                <span>{suffix}</span>
            </strong>
        </article>
    );
}

function Panel({ title, icon, children }) {
    return (
        <section className="panel">
            <div className="compact-heading">
                {icon}
                <h3>{title}</h3>
            </div>
            {children}
        </section>
    );
}

function List({ items }) {
    if (!items?.length) return <p className="muted">Add more logs to reveal this insight.</p>;
    return (
        <ul className="clean-list">
            {items.map((item) => (
                <li key={item}>{item}</li>
            ))}
        </ul>
    );
}

function formatNumber(value) {
    if (value === undefined || value === null) return "0.0";
    return Number(value).toFixed(1);
}

createRoot(document.getElementById("root")).render(<App />);
