const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:9090/api";

async function request(path, options = {}) {
    const response = await fetch(`${API_BASE}${path}`, {
        headers: { "Content-Type": "application/json" },
        ...options,
    });

    if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`);
    }

    return response.json();
}

export function getEntries() {
    return request("/entries");
}

export function getInsights() {
    return request("/insights");
}

export function createEntry(entry) {
    return request("/entries", {
        method: "POST",
        body: JSON.stringify(entry),
    });
}

export function sendChat(message, currentEntryId) {
    return request("/chat", {
        method: "POST",
        body: JSON.stringify({ message, currentEntryId }),
    });
}
