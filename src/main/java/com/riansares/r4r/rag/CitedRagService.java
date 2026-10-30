package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cited RAG service that retrieves evidence and generates answers with citations.
 */
public class CitedRagService {


    private final PgVectorKnowledgeStore knowledgeStore;
    private final ChatModel chatModel;
    private final int retrievalTopK;
    private final double minScore;
    private static final Pattern ABSTAIN_TOKEN = Pattern.compile("(?i)(?<![a-z0-9_])_*R4R_ABST(?:AIN|INERSE|INENT)_*(?![a-z0-9_])");
    private static final Pattern ONLY_CITATIONS =
            Pattern.compile("^(?:\\[S\\d+\\][\\s,;:.]*)+$");
    private static final Pattern REFERENCE_GROUP = Pattern.compile("\\[(S\\d+(?:\\s*,\\s*S\\d+)*)\\]");
    private static final Pattern NATURAL_ABSTENTION = Pattern.compile(
            "(?iu)^(?:(?:lo siento[,;:]?\\s*)?(?:no (?:dispongo de|tengo|hay|se proporciona|se dispone de) "
                    + "(?:la |una )?(?:suficiente )?(?:información|evidencia|datos)(?: suficiente(?:s)?)?"
                    + "(?: (?:en|para|sobre|en los) [^.!?]*)?|"
                    + "(?:la |el )?(?:información|evidencia|contexto) (?:disponible|proporcionad[oa]|suministrad[oa]) "
                    + "(?:no (?:es|contiene) suficiente|no permite responder)(?: [^.!?]*)?|"
                    + "(?:i(?:'m| am) sorry[,;:]?\\s*)?(?:i (?:do not|don't) have|there (?:is not|isn't)) "
                    + "(?:enough|sufficient) (?:information|evidence|data)(?: [^.!?]*)?|"
                    + "(?:the )?(?:provided|available|supplied) (?:information|evidence|context) "
                    + "(?:is insufficient|is not sufficient|does not (?:provide enough information|allow me to answer))(?: [^.!?]*)?))"
                    + "[.!]?$" );
    // Each sentence must describe the absence of evidence or inability to answer.
    private static final Pattern ABSTENTION_SENTENCE = Pattern.compile(
            "(?iu)^(?:(?:therefore|thus|therefore,|thus,|por tanto,?|por ello,?|en consecuencia,?)\\s+)?(?:"
                    + "(?:the )?(?:provided |available |supplied )?(?:evidence|information|context|sources?) "
                    + "(?:does not|do not|doesn't|don't) (?:include|contain|provide|address|specify|state|say)\\b[^.!?]*|"
                    + "(?:and |y )?(?:do not|does not|don't|doesn't) (?:address|contain|provide|specify)\\b[^.!?]*|"
                    + "(?:i cannot|i can't|i am unable to|i'm unable to) (?:determine|answer|say|tell|identify)\\b[^.!?]*|"
                    + "(?:la |el |las |los )?(?:evidencia|información|contexto|fuentes?|documentos?) "
                    + "(?:disponible |proporcionad[oa]s? |aportad[oa]s? |consultad[oa]s? )?"
                    + "no (?:incluye[n]?|contiene[n]?|proporciona[n]?|aborda[n]?|especifica[n]?|indica[n]?)\\b[^.!?]*|"
                    + "(?:y )?no (?:abordan|contienen|proporcionan|especifican|indican)\\b[^.!?]*|"
                    + "(?:no (?:puedo|es posible|se puede)|soy incapaz de) (?:determinar|responder|saber|identificar|indicar)\\b[^.!?]*"
                    + ")[.!?]?$" );
    private static final Pattern CONTRAST = Pattern.compile("(?iu)\\b(?:but|however|nevertheless|pero|aunque|sin embargo|no obstante)\\b");
    /**
     * Creates a new cited RAG service.
     *
     * @param knowledgeStore the vector store for evidence retrieval
     * @param chatModel      the chat model for answer generation
     */
    public CitedRagService(
            PgVectorKnowledgeStore knowledgeStore,
            ChatModel chatModel,
            int retrievalTopK,
            double minScore) {
        this.knowledgeStore = Objects.requireNonNull(knowledgeStore, "knowledgeStore");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.retrievalTopK = retrievalTopK;
        this.minScore = minScore;
    }

    /**
     * Processes a question and returns a cited answer.
     *
     * @param question the user's question
     * @return the result with answer, abstention flag, and citations
     */
    public RagResult answer(String question) {
        return answer(question, null, null);
    }

    public RagResult answer(String question, String retrievalQuestion, String responseLanguage) {
        validateQuestion(question);
        String searchQuestion = retrievalQuestion == null || retrievalQuestion.isBlank()
                ? question : retrievalQuestion;
        List<MarkdownChunk> chunks = retrieveEvidence(searchQuestion);

        if (chunks.isEmpty()) {
            return RagResult.abstain();
        }

        Prompt prompt = buildPrompt(question + "\n/no_think", chunks, responseLanguage);
        ChatResponse response = chatModel.call(prompt);
        String answer = extractAnswer(response);

        if (answer == null
                || answer.isBlank()
                || ABSTAIN_TOKEN.matcher(answer).find()
                || isNaturalAbstention(answer)) {
            return RagResult.abstain();
        }
        answer = normalizeReferences(answer, chunks.size());
        if (answer == null || answer.isBlank() || ONLY_CITATIONS.matcher(answer.trim()).matches()) {
            return RagResult.abstain();
        }
        List<Citation> citations = buildCitations(chunks, answer);

        return RagResult.ofAnswer(answer, citations);
    }

    private boolean isNaturalAbstention(String answer) {
        String text = REFERENCE_GROUP.matcher(answer).replaceAll("").replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || CONTRAST.matcher(text).find()) {
            return false;
        }
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            String trimmed = sentence.trim();
            if (!NATURAL_ABSTENTION.matcher(trimmed).matches()
                    && !ABSTENTION_SENTENCE.matcher(trimmed).matches()) {
                return false;
            }
        }
        return true;
    }

    private String normalizeReferences(String answer, int sourceCount) {
        Matcher groups = REFERENCE_GROUP.matcher(answer);
        StringBuffer normalized = new StringBuffer();
        while (groups.find()) {
            StringBuilder replacement = new StringBuilder();
            for (String reference : groups.group(1).split("\\s*,\\s*")) {
                try {
                    int index = Integer.parseInt(reference.substring(1));
                    if (index < 1 || index > sourceCount || !reference.equals("S" + index)) {
                        return null;
                    }
                } catch (NumberFormatException ex) {
                    return null;
                }
                replacement.append('[').append(reference).append(']');
            }
            groups.appendReplacement(normalized, Matcher.quoteReplacement(replacement.toString()));
        }
        groups.appendTail(normalized);
        return normalized.toString();
    }

    private void validateQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
    }

    private List<MarkdownChunk> retrieveEvidence(String question) {
        return knowledgeStore.search(
                question,
                retrievalTopK,
                minScore);
    }

    private Prompt buildPrompt(String question, List<MarkdownChunk> chunks, String responseLanguage) {
        StringBuilder promptText = new StringBuilder();

        promptText.append("You are a helpful assistant. Use the following evidence to answer the question.\n\n*****\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            String label = "[S" + (i + 1) + "]";
            promptText.append(label).append("\n");
            promptText.append(chunk.content()).append("\n\n");
        }

        promptText.append("*****\nQuestion: ").append(question).append("\n\n");
        if ("en".equalsIgnoreCase(responseLanguage)) {
            promptText.append("Respond in English.\n");
        } else if ("es".equalsIgnoreCase(responseLanguage)) {
            promptText.append("Respond in Spanish.\n");
        } else {
            promptText.append("Respond in the same language as the question.\n");
        }
        promptText.append("""
        *****
        Answer the question directly using only facts supported by the supplied evidence.
        Use 1–3 concise sentences when sufficient. Do not invent facts, repeat yourself, or add tangential information.
        Cite supporting sources using [S1], [S2], etc. only where relevant; do not cite unused sources.
        Citations must not be the entire answer.
    
        If the evidence does not support an answer, respond with exactly:
        __R4R_ABSTAIN__
        """);

        return new Prompt(promptText.toString());
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("Chat model returned null response");
        }
        return response.getResult().getOutput().getText();
    }

    private List<Citation> buildCitations(List<MarkdownChunk> chunks, String answer) {
        List<Citation> citations = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            MarkdownChunk chunk = chunks.get(i);
            String label = "[S" + (i + 1) + "]";
            if (answer.contains(label)) {
                citations.add(Citation.fromMarkdownChunk(label, chunk));
            }
        }
        return List.copyOf(citations);
    }
}
