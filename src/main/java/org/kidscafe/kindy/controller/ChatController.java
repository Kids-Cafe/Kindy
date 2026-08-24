package org.kidscafe.kindy.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kidscafe.kindy.dto.ChatDTO;
import org.kidscafe.kindy.dto.ReportDTO;
import org.kidscafe.kindy.dto.ResultDTO;
import org.kidscafe.kindy.service.IAccessService;
import org.kidscafe.kindy.service.IChatService;
import org.kidscafe.kindy.service.IUserService;
import org.kidscafe.kindy.service.ServiceUnavailableException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final IChatService chatService;
    private final IAccessService accessService;
    /** Only for resolving the report a data card pins — see {@link #send}. */
    private final IUserService userService;

    @GetMapping(value = "list")
    public ResultDTO<List<ChatDTO>> list(HttpSession session, @RequestParam long kindergartenId) {
        log.info("Calling list");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            // A conversation is private to its two sides, so the kindergarten filter narrows the
            // user's own chats rather than exposing everyone else's.
            if (kindergartenId > 0) {
                if (!accessService.canView(kindergartenId, userId)) return ResultDTO.error("INVALID_ACCESS");
                return ResultDTO.success("QUERY_COMPLETE", chatService.getList(kindergartenId).stream()
                        .filter(c -> userId.equals(c.getHost()) || userId.equals(c.getClient()))
                        .toList());
            }
            return ResultDTO.success("QUERY_COMPLETE", chatService.getList(userId));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    @GetMapping(value = "info")
    public ResultDTO<ChatDTO> info(HttpSession session, @RequestParam long id) {
        log.info("Calling info");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            ChatDTO rDTO = chatService.getInfo(id);
            if (rDTO == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(rDTO, userId)) return ResultDTO.error("INVALID_ACCESS");

            return ResultDTO.success("QUERY_COMPLETE", rDTO);
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // Returns the chat (with its id) so the caller can start sending immediately. If the pair
    // already has a conversation, that one comes back — opening a second thread for the same two
    // people would split the history, and whichever row a later lookup happened to pick would
    // silently hide the rest of it.
    @PostMapping(value = "create")
    public ResultDTO<ChatDTO> create(HttpSession session,
                                     @RequestParam long kindergartenId,
                                     @RequestParam(required = false) String host,
                                     @RequestParam(required = false) String client) {
        log.info("Calling create");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        // The session user is one side of the chat unless they said otherwise.
        if (host == null) host = userId;
        if (client == null) client = userId;
        if (!userId.equals(host) && !userId.equals(client)) return ResultDTO.error("INVALID_ACCESS");

        // Both sides have to belong to the kindergarten the chat is filed under, otherwise anyone
        // could open a thread with a stranger by naming an arbitrary kindergarten.
        if (!accessService.canView(kindergartenId, userId)) return ResultDTO.error("INVALID_ACCESS");
        String other = userId.equals(host) ? client : host;
        if (!accessService.canView(kindergartenId, other)) return ResultDTO.error("INVALID_PARAMETER");

        try {
            return ResultDTO.success("CREATE_COMPLETE", chatService.ensure(kindergartenId, host, client));
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // NUM is assigned by the database, so two senders racing on the same chat can't collide.
    //
    // ROLE, AUTHOR and REPORT_ID are deliberately not parameters. Everything a person sends is a
    // `user` turn by them; `assistant` turns are written only by `request`, from what the model
    // actually returned, and carry no author. Letting a caller name its own role would let a child
    // put words in the AI's mouth — and worse, those words would come back as context on the next
    // turn, steering the model with text it never produced. Letting it name its own author is the
    // same hole pointed at the other participant: in a two-person chat it would forge their side of
    // the conversation. And letting it name its own report id would pin some other child's report
    // into this thread, past the gate below. All three come from the server instead.
    //
    // `childId` is required for a data card and ignored for TEXT: a card is about a particular
    // child, and the chat itself does not record which one — it records a kindergarten and two
    // people, and a parent may have more than one child enrolled.
    @PostMapping(value = "send")
    public ResultDTO<ChatDTO.MessageDTO> send(HttpSession session,
                                @RequestParam long chatId,
                                @RequestParam String content,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String childId) {
        log.info("Calling send");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");
        if (content == null || content.isBlank()) return ResultDTO.error("INVALID_PARAMETER");

        try {
            ChatDTO chat = chatService.getInfo(chatId);
            if (chat == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(chat, userId)) return ResultDTO.error("INVALID_ACCESS");

            ChatDTO.MessageDTO pDTO = new ChatDTO.MessageDTO();
            pDTO.setChatId(chatId);
            pDTO.setContent(content);
            pDTO.setType(type == null ? ChatDTO.MessageDTO.Type.TEXT : ChatDTO.MessageDTO.Type.valueOf(type));
            pDTO.setRole(ChatDTO.MessageDTO.Role.user);
            pDTO.setAuthor(userId);

            ReportDTO.Category category = pDTO.getType().category();
            if (category != null) {
                if (childId == null || childId.isBlank()) return ResultDTO.error("MISSING_PARAMETER");
                // The same gate as `user/report/list`: a card hands the other participant a copy of
                // this child's report, so the sender has to be someone who may read it.
                if (!accessService.canViewChild(userId, childId)) return ResultDTO.error("INVALID_ACCESS");

                // Pinned now, at the version the sender is looking at. From here the card is a
                // statement about a particular report rather than a standing query — regenerating
                // the category writes a new row and leaves this one, and the message keeps showing
                // what it showed the day it was sent.
                ReportDTO report = userService.getReportInfo(childId, category);
                // Nothing to send. The client generates before inserting a card, so this is the
                // case where generation produced nothing — too little conversation to report on.
                if (report == null) return ResultDTO.error("NOT_FOUND");

                pDTO.setReportId(report.getId());
            }

            return ResultDTO.success("SEND_COMPLETE", chatService.appendMessageAndRead(pDTO));
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    // Everything past this point leans on an external service that can be slow, down, or fussy
    // about its input. Those failures are ordinary here, so they are answered with a ResultDTO like
    // any other error — an uncaught exception would leave Spring to render an HTML 500 page, which
    // a client parsing the {status, code, data} envelope can only report as a parse error.
    @PostMapping(value = "transcribe")
    // `required = false` so a call with no file is answered with INVALID_PARAMETER below, rather
    // than by Spring's own error page — which is not a ResultDTO and so reaches the client as an
    // unparseable response instead of an error it can name.
    public ResultDTO<String> transcribe(HttpSession session,
                                        @RequestParam(value = "file", required = false) MultipartFile file) {
        log.info("Calling transcribe");

        // Not tied to a chat, but it bills an external API, so it isn't open to anonymous callers.
        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");
        if (file == null || file.isEmpty()) return ResultDTO.error("INVALID_PARAMETER");

        try {
            String result = chatService.transcribe(file.getResource());
            log.info(result);
            return ResultDTO.success("TRANSCRIPTION_COMPLETE", result == null ? "" : result.trim());
        } catch (ServiceUnavailableException e) {
            // No speech service configured at all, which is not the same as one that failed: there
            // is nothing to retry and nothing the client can send differently.
            log.info(e.toString());
            return ResultDTO.error("NOT_AVAILABLE");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("TRANSCRIPTION_FAILED");
        }
    }

    // `partner` names the character to answer as (kio/kina). It is the client's to keep — nothing
    // stores it here — so it comes in with the request, and an unknown or missing name answers as
    // the default character rather than refusing the turn.
    @PostMapping(value = "request")
    public ResultDTO<ChatDTO.MessageDTO> request(HttpSession session,
            @RequestParam long chatId,
            @RequestParam(required = false) String partner) {
        log.info("Calling request");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            ChatDTO chat = chatService.getInfo(chatId);
            if (chat == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(chat, userId)) return ResultDTO.error("INVALID_ACCESS");

            ChatDTO.MessageDTO reply = chatService.requestMessage(chatId, partner);
            if (reply == null) return ResultDTO.error("GENERATION_FAILED");

            return ResultDTO.success("SEND_COMPLETE", reply);
        } catch (ServiceUnavailableException e) {
            log.info(e.toString());
            return ResultDTO.error("NOT_AVAILABLE");
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("GENERATION_FAILED");
        }
    }

    /**
     * `send` and `request` in one call — the whole of a turn against an AI partner.
     *
     * Both messages are returned so the caller can render the exchange without re-reading the
     * conversation. If the model fails after the child's message is stored, that message stays
     * stored and `reply` is null: the child said it, and losing it to retry the model would be
     * worse than answering late.
     * <p>
     * TEXT only. A data card is not something anyone said and there is nothing here for the model to
     * answer, but the real reason for the check is narrower: cards are pinned to a report id by
     * `send`, and a card written through this path would have none — the drift
     * docs/migration-report-identity.sql exists to end, let back in through the other door. No
     * screen sends one; the guard is so that none quietly starts.
     */
    @PostMapping(value = "say")
    public ResultDTO<ChatDTO.TurnDTO> say(HttpSession session,
                                          @RequestParam long chatId,
                                          @RequestParam String content,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String partner) {
        log.info("Calling say");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");
        if (content == null || content.isBlank()) return ResultDTO.error("INVALID_PARAMETER");

        ChatDTO.MessageDTO sent;
        try {
            ChatDTO chat = chatService.getInfo(chatId);
            if (chat == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(chat, userId)) return ResultDTO.error("INVALID_ACCESS");

            ChatDTO.MessageDTO pDTO = new ChatDTO.MessageDTO();
            pDTO.setChatId(chatId);
            pDTO.setContent(content);
            pDTO.setType(type == null ? ChatDTO.MessageDTO.Type.TEXT : ChatDTO.MessageDTO.Type.valueOf(type));
            if (pDTO.getType() != ChatDTO.MessageDTO.Type.TEXT) return ResultDTO.error("INVALID_PARAMETER");
            pDTO.setRole(ChatDTO.MessageDTO.Role.user);
            pDTO.setAuthor(userId);

            sent = chatService.appendMessageAndRead(pDTO);
        } catch (IllegalArgumentException e) {
            return ResultDTO.error("INVALID_PARAMETER");
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("UNKNOWN_ERROR");
        }

        try {
            ChatDTO.MessageDTO reply = chatService.requestMessage(chatId, partner);
            if (reply == null) return ResultDTO.error("GENERATION_FAILED", new ChatDTO.TurnDTO(sent, null));

            return ResultDTO.success("SEND_COMPLETE", new ChatDTO.TurnDTO(sent, reply));
        } catch (ServiceUnavailableException e) {
            // The turn still carries what was said: the message was stored before the model was
            // asked, so it must come back either way or the client loses it.
            log.info(e.toString());
            return ResultDTO.error("NOT_AVAILABLE", new ChatDTO.TurnDTO(sent, null));
        } catch (Exception e) {
            log.info(e.toString());
            return ResultDTO.error("GENERATION_FAILED", new ChatDTO.TurnDTO(sent, null));
        }
    }

    // These two answer with audio, not the ResultDTO envelope, so failures are plain status codes:
    // 401 when the session is gone (the client tells that apart from a forbidden resource and
    // re-authenticates), 502 when the speech service upstream is the one that failed.
    @PostMapping(value = "synthesize", produces = "audio/wav")
    public ResponseEntity<Resource> synthesize(HttpSession session, @RequestParam(value = "text") String text) {
        log.info("Calling synthesize");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (text == null || text.isBlank()) return ResponseEntity.badRequest().build();

        try {
            Resource audioStream = chatService.synthesize(text);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(audioStream);
        } catch (ServiceUnavailableException e) {
            // 503 rather than the 502 below: nothing upstream failed, because there is no upstream.
            log.info(e.toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            log.info(e.toString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    // `partner` picks the voice as well as the words: the character's own speed during synthesis,
    // and their model and pitch during conversion. Left off, this speaks in the neutral voice the
    // endpoint has always used.
    @PostMapping(value = "speak", produces = "audio/wav")
    public ResponseEntity<Resource> speak(HttpSession session,
                                          @RequestParam(value = "text") String text,
                                          @RequestParam(required = false) String partner) {
        log.info("Calling speak");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (text == null || text.isBlank()) return ResponseEntity.badRequest().build();

        // The two halves are caught separately, and this one is not retried. It used to be: any
        // failure at all fell through to a second `synthesize` call, which was free against a
        // self-hosted server and is a second bill against a cloud one — including in the case where
        // the thing that had just failed *was* the synthesizer, so an outage charged for every
        // utterance twice and answered none of them. It also charged twice on the ordinary path of a
        // deployment with no STS_URL, where conversion throws every time by design.
        Resource speech;
        try {
            speech = chatService.synthesize(text, partner);
        } catch (ServiceUnavailableException e) {
            // 503 rather than the 502 below: nothing upstream failed, because there is no upstream —
            // no TTS_URL, or no credentials to reach it with.
            log.info(e.toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (Exception e) {
            log.info(e.toString());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        try {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav"))
                    .body(chatService.convert(speech, partner));
        } catch (Exception e) {
            // Voice conversion is the fragile half, and it is also the optional one: an unconfigured
            // STS_URL arrives here as a ServiceUnavailableException and is meant to. Losing the
            // character's voice is a much smaller loss than losing the voice, so what goes back is
            // the speech we already have — still at the character's own pace and in their own voice,
            // which is the part of them that survives without conversion.
            //
            // Nothing is re-synthesized. `speech` is fully buffered, so handing it to the response
            // after conversion has read it costs one more pass over a byte array and not one more
            // request to a paid API.
            log.info(e.toString());
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(speech);
        }
    }

    @GetMapping(value = "messages")
    public ResultDTO<List<ChatDTO.MessageDTO>> messages(HttpSession session, @RequestParam long id) {
        log.info("Calling messages");

        String userId = (String) session.getAttribute("SESSION_USER_ID");
        if (userId == null) return ResultDTO.error("INVALID_ACCESS");

        try {
            ChatDTO chat = chatService.getInfo(id);
            if (chat == null) return ResultDTO.error("NOT_FOUND");
            if (!isParticipant(chat, userId)) return ResultDTO.error("INVALID_ACCESS");

            return ResultDTO.success("QUERY_COMPLETE", chatService.getMessages(id));
        } catch (Exception e) {
            log.info(e.getMessage());
            return ResultDTO.error("UNKNOWN_ERROR");
        }
    }

    /** Chats are two-sided: only the host and the client may read or write one. */
    private static boolean isParticipant(ChatDTO chat, String userId) {
        return userId.equals(chat.getHost()) || userId.equals(chat.getClient());
    }
}
