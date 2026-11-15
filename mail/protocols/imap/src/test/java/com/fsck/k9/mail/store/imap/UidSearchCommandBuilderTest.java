package com.fsck.k9.mail.store.imap;


import java.util.Collections;
import java.util.Set;

import net.thunderbird.core.common.mail.Flag;
import net.thunderbird.core.common.mail.SearchDate;
import net.thunderbird.core.common.mail.ServerSearchCriteria;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class UidSearchCommandBuilderTest {

    @Test
    public void build_withFullTextSearch() {
        String command = new UidSearchCommandBuilder()
                .performFullTextSearch(true)
                .requiredFlags(Collections.singleton(Flag.FLAGGED))
                .forbiddenFlags(Collections.singleton(Flag.DELETED))
                .queryString("query")
                .build();

        assertEquals("UID SEARCH TEXT \"query\" FLAGGED NOT DELETED", command);
    }

    @Test
    public void build_withoutFullTextSearch() {
        String command = new UidSearchCommandBuilder()
                .performFullTextSearch(false)
                .requiredFlags(null)
                .forbiddenFlags(Collections.singleton(Flag.DELETED))
                .queryString("query")
                .build();

        assertEquals("UID SEARCH OR OR OR OR SUBJECT \"query\" FROM \"query\" TO \"query\" CC \"query\"" +
                " BCC \"query\" NOT DELETED", command);
    }

    @Test
    public void build_withMultipleWords_shouldRequireEveryWord() {
        String command = new UidSearchCommandBuilder()
                .performFullTextSearch(false)
                .queryString("alice invoice")
                .build();

        assertEquals("UID SEARCH OR OR OR OR SUBJECT \"alice\" FROM \"alice\" TO \"alice\" CC \"alice\"" +
                " BCC \"alice\" OR OR OR OR SUBJECT \"invoice\" FROM \"invoice\" TO \"invoice\"" +
                " CC \"invoice\" BCC \"invoice\"", command);
    }

    @Test
    public void build_withMultipleWordsAndFullTextSearch_shouldRequireEveryWord() {
        String command = new UidSearchCommandBuilder()
                .performFullTextSearch(true)
                .queryString("alice invoice")
                .build();

        assertEquals("UID SEARCH TEXT \"alice\" TEXT \"invoice\"", command);
    }

    @Test
    public void build_withQuotedText_shouldSearchForPhrase() {
        String command = new UidSearchCommandBuilder()
                .performFullTextSearch(true)
                .queryString("\"alice example\" invoice")
                .build();

        assertEquals("UID SEARCH TEXT \"alice example\" TEXT \"invoice\"", command);
    }

    @Test
    public void build_withCriteriaSearchingHeaders_shouldSearchAllHeaders() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().text("query").build())
                .build();

        assertEquals("UID SEARCH OR OR OR OR SUBJECT \"query\" FROM \"query\" TO \"query\" CC \"query\"" +
                " BCC \"query\"", command);
    }

    @Test
    public void build_withCriteriaSearchingTheBody_shouldSearchTheBody() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder()
                        .text("query")
                        .textScope(ServerSearchCriteria.TextScope.BODY)
                        .build())
                .build();

        assertEquals("UID SEARCH BODY \"query\"", command);
    }

    @Test
    public void build_withCriteriaSearchingEverywhere_shouldSearchText() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder()
                        .text("query")
                        .textScope(ServerSearchCriteria.TextScope.EVERYWHERE)
                        .build())
                .build();

        assertEquals("UID SEARCH TEXT \"query\"", command);
    }

    @Test
    public void build_withCriteriaContainingMultipleWords_shouldRequireEveryWord() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().text("alice invoice").build())
                .build();

        assertEquals("UID SEARCH OR OR OR OR SUBJECT \"alice\" FROM \"alice\" TO \"alice\" CC \"alice\"" +
                " BCC \"alice\" OR OR OR OR SUBJECT \"invoice\" FROM \"invoice\" TO \"invoice\"" +
                " CC \"invoice\" BCC \"invoice\"", command);
    }

    @Test
    public void build_withCriteriaWithFromAddress() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().from("alice@example.org").build())
                .build();

        assertEquals("UID SEARCH FROM \"alice@example.org\"", command);
    }

    @Test
    public void build_withCriteriaWithToAddress() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().to("bob@example.org").build())
                .build();

        assertEquals("UID SEARCH TO \"bob@example.org\"", command);
    }

    @Test
    public void build_withCriteriaWithSubject() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().subject("invoice").build())
                .build();

        assertEquals("UID SEARCH SUBJECT \"invoice\"", command);
    }

    @Test
    public void build_withCriteriaReceivedWithinDays() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().receivedWithinDays(7).build())
                .build();

        assertEquals("UID SEARCH YOUNGER 604800", command);
    }

    @Test
    public void build_withCriteriaReceivedSince() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().receivedSince(new SearchDate(2024, 1, 5)).build())
                .build();

        assertEquals("UID SEARCH SINCE 5-Jan-2024", command);
    }

    @Test
    public void build_withCriteriaReceivedBefore() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().receivedBefore(new SearchDate(2024, 3, 15)).build())
                .build();

        assertEquals("UID SEARCH BEFORE 15-Mar-2024", command);
    }

    @Test
    public void build_withCriteriaLargerThanBytes() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().largerThanBytes(1024L).build())
                .build();

        assertEquals("UID SEARCH LARGER 1024", command);
    }

    @Test
    public void build_withCriteriaSmallerThanBytes() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().smallerThanBytes(2048L).build())
                .build();

        assertEquals("UID SEARCH SMALLER 2048", command);
    }

    @Test
    public void build_withCriteriaWithAttachment() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().hasAttachment(true).build())
                .build();

        assertEquals("UID SEARCH HEADER Content-Type \"multipart/mixed\"", command);
    }

    @Test
    public void build_withCriteriaWithFlags() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder()
                        .requiredFlags(Collections.singleton(Flag.FLAGGED))
                        .forbiddenFlags(Collections.singleton(Flag.DELETED))
                        .build())
                .build();

        assertEquals("UID SEARCH FLAGGED NOT DELETED", command);
    }

    @Test
    public void build_withEmptyCriteria_shouldSearchAllMessages() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder().build())
                .build();

        assertEquals("UID SEARCH ALL", command);
    }

    @Test
    public void build_withMultipleCriteria_shouldCombineThem() {
        String command = new UidSearchCommandBuilder()
                .criteria(new CriteriaBuilder()
                        .text("invoice")
                        .from("alice@example.org")
                        .receivedSince(new SearchDate(2024, 1, 5))
                        .largerThanBytes(1024L)
                        .hasAttachment(true)
                        .requiredFlags(Collections.singleton(Flag.FLAGGED))
                        .forbiddenFlags(Collections.singleton(Flag.DELETED))
                        .build())
                .build();

        assertEquals("UID SEARCH OR OR OR OR SUBJECT \"invoice\" FROM \"invoice\" TO \"invoice\" CC \"invoice\"" +
                " BCC \"invoice\" FROM \"alice@example.org\" SINCE 5-Jan-2024 LARGER 1024" +
                " HEADER Content-Type \"multipart/mixed\" FLAGGED NOT DELETED", command);
    }


    /**
     * Builds a {@link ServerSearchCriteria} without having to name all of its properties.
     */
    private static final class CriteriaBuilder {
        private String text;
        private ServerSearchCriteria.TextScope textScope = ServerSearchCriteria.TextScope.HEADERS;
        private String from;
        private String to;
        private String subject;
        private SearchDate receivedSince;
        private SearchDate receivedBefore;
        private Integer receivedWithinDays;
        private Long largerThanBytes;
        private Long smallerThanBytes;
        private boolean hasAttachment;
        private Set<Flag> requiredFlags = Collections.emptySet();
        private Set<Flag> forbiddenFlags = Collections.emptySet();

        CriteriaBuilder text(String text) {
            this.text = text;
            return this;
        }

        CriteriaBuilder textScope(ServerSearchCriteria.TextScope textScope) {
            this.textScope = textScope;
            return this;
        }

        CriteriaBuilder from(String from) {
            this.from = from;
            return this;
        }

        CriteriaBuilder to(String to) {
            this.to = to;
            return this;
        }

        CriteriaBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }

        CriteriaBuilder receivedSince(SearchDate receivedSince) {
            this.receivedSince = receivedSince;
            return this;
        }

        CriteriaBuilder receivedBefore(SearchDate receivedBefore) {
            this.receivedBefore = receivedBefore;
            return this;
        }

        CriteriaBuilder receivedWithinDays(Integer receivedWithinDays) {
            this.receivedWithinDays = receivedWithinDays;
            return this;
        }

        CriteriaBuilder largerThanBytes(Long largerThanBytes) {
            this.largerThanBytes = largerThanBytes;
            return this;
        }

        CriteriaBuilder smallerThanBytes(Long smallerThanBytes) {
            this.smallerThanBytes = smallerThanBytes;
            return this;
        }

        CriteriaBuilder hasAttachment(boolean hasAttachment) {
            this.hasAttachment = hasAttachment;
            return this;
        }

        CriteriaBuilder requiredFlags(Set<Flag> requiredFlags) {
            this.requiredFlags = requiredFlags;
            return this;
        }

        CriteriaBuilder forbiddenFlags(Set<Flag> forbiddenFlags) {
            this.forbiddenFlags = forbiddenFlags;
            return this;
        }

        ServerSearchCriteria build() {
            return new ServerSearchCriteria(text, textScope, from, to, subject, receivedSince, receivedBefore,
                    receivedWithinDays, largerThanBytes, smallerThanBytes, hasAttachment, requiredFlags,
                    forbiddenFlags);
        }
    }
}
