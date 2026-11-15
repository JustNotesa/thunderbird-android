package com.fsck.k9.mail.store.imap;


import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.thunderbird.core.common.mail.Flag;
import net.thunderbird.core.common.mail.SearchDate;
import net.thunderbird.core.common.mail.SearchQueryKt;
import net.thunderbird.core.common.mail.ServerSearchCriteria;


class UidSearchCommandBuilder {
    private static final String[] MONTH_NAMES = { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    private String queryString;
    private boolean performFullTextSearch;
    private Set<Flag> requiredFlags;
    private Set<Flag> forbiddenFlags;
    private ServerSearchCriteria criteria;


    public UidSearchCommandBuilder queryString(String queryString) {
        this.queryString = queryString;
        return this;
    }

    public UidSearchCommandBuilder performFullTextSearch(boolean performFullTextSearch) {
        this.performFullTextSearch = performFullTextSearch;
        return this;
    }

    public UidSearchCommandBuilder requiredFlags(Set<Flag> requiredFlags) {
        this.requiredFlags = requiredFlags;
        return this;
    }

    public UidSearchCommandBuilder forbiddenFlags(Set<Flag> forbiddenFlags) {
        this.forbiddenFlags = forbiddenFlags;
        return this;
    }

    /**
     * Search for the messages the given criteria describe. When this is set, the other properties of this builder are
     * ignored.
     */
    public UidSearchCommandBuilder criteria(ServerSearchCriteria criteria) {
        this.criteria = criteria;
        return this;
    }

    public String build() {
        StringBuilder builder = new StringBuilder(Commands.UID_SEARCH);
        if (criteria != null) {
            addCriteria(builder, criteria);
        } else {
            addQueryString(builder);
            addFlags(builder, requiredFlags, false);
            addFlags(builder, forbiddenFlags, true);
        }
        return builder.toString();
    }

    private void addCriteria(StringBuilder builder, ServerSearchCriteria criteria) {
        addCriteriaText(builder, criteria);
        addCriteriaAddresses(builder, criteria);
        addCriteriaDates(builder, criteria);
        addCriteriaSizes(builder, criteria);
        addCriteriaAttachment(builder, criteria);
        addFlags(builder, criteria.getRequiredFlags(), false);
        addFlags(builder, criteria.getForbiddenFlags(), true);

        if (builder.length() == Commands.UID_SEARCH.length()) {
            builder.append(" ALL");
        }
    }

    private void addCriteriaText(StringBuilder builder, ServerSearchCriteria criteria) {
        String text = criteria.getText();
        if (text == null) {
            return;
        }

        // A message has to match every term of the text. Search keys that follow each other are combined with AND.
        List<String> searchTerms = SearchQueryKt.splitSearchQuery(text);
        if (searchTerms.isEmpty()) {
            searchTerms = Collections.singletonList(text);
        }

        for (String searchTerm : searchTerms) {
            addTextTerm(builder, searchTerm, criteria.getTextScope());
        }
    }

    private void addTextTerm(StringBuilder builder, String searchTerm, ServerSearchCriteria.TextScope textScope) {
        String encodedTerm = ImapUtility.encodeString(searchTerm);
        ServerSearchCriteria.TextScope scope = textScope != null ? textScope : ServerSearchCriteria.TextScope.HEADERS;

        switch (scope) {
            case BODY: {
                builder.append(" BODY ").append(encodedTerm);
                break;
            }
            case EVERYWHERE: {
                builder.append(" TEXT ").append(encodedTerm);
                break;
            }
            case HEADERS: {
                builder.append(" OR OR OR OR SUBJECT ").append(encodedTerm)
                        .append(" FROM ").append(encodedTerm)
                        .append(" TO ").append(encodedTerm)
                        .append(" CC ").append(encodedTerm)
                        .append(" BCC ").append(encodedTerm);
                break;
            }
            default: {
                throw new IllegalStateException("Unsupported text scope: " + textScope);
            }
        }
    }

    private void addCriteriaAddresses(StringBuilder builder, ServerSearchCriteria criteria) {
        addSearchKey(builder, " FROM", criteria.getFrom());
        addSearchKey(builder, " TO", criteria.getTo());
        addSearchKey(builder, " SUBJECT", criteria.getSubject());
    }

    private void addSearchKey(StringBuilder builder, String searchKey, String value) {
        if (value == null) {
            return;
        }

        builder.append(searchKey).append(" ").append(ImapUtility.encodeString(value));
    }

    private void addCriteriaDates(StringBuilder builder, ServerSearchCriteria criteria) {
        Integer receivedWithinDays = criteria.getReceivedWithinDays();
        if (receivedWithinDays != null) {
            builder.append(" YOUNGER ").append(receivedWithinDays.longValue() * SECONDS_PER_DAY);
        }

        SearchDate receivedSince = criteria.getReceivedSince();
        if (receivedSince != null) {
            builder.append(" SINCE ").append(formatDate(receivedSince));
        }

        SearchDate receivedBefore = criteria.getReceivedBefore();
        if (receivedBefore != null) {
            builder.append(" BEFORE ").append(formatDate(receivedBefore));
        }
    }

    private String formatDate(SearchDate date) {
        return date.getDay() + "-" + MONTH_NAMES[date.getMonth() - 1] + "-" + date.getYear();
    }

    private void addCriteriaSizes(StringBuilder builder, ServerSearchCriteria criteria) {
        Long largerThanBytes = criteria.getLargerThanBytes();
        if (largerThanBytes != null) {
            builder.append(" LARGER ").append(largerThanBytes);
        }

        Long smallerThanBytes = criteria.getSmallerThanBytes();
        if (smallerThanBytes != null) {
            builder.append(" SMALLER ").append(smallerThanBytes);
        }
    }

    private void addCriteriaAttachment(StringBuilder builder, ServerSearchCriteria criteria) {
        if (criteria.getHasAttachment()) {
            builder.append(" HEADER Content-Type \"multipart/mixed\"");
        }
    }

    private void addQueryString(StringBuilder builder) {
        if (queryString == null) {
            return;
        }

        // A message has to match every term of the query. Search keys that follow each other are combined with AND.
        List<String> searchTerms = SearchQueryKt.splitSearchQuery(queryString);
        if (searchTerms.isEmpty()) {
            searchTerms = Collections.singletonList(queryString);
        }

        for (String searchTerm : searchTerms) {
            String encodedTerm = ImapUtility.encodeString(searchTerm);
            if (performFullTextSearch) {
                builder.append(" TEXT ").append(encodedTerm);
            } else {
                builder.append(" OR OR OR OR SUBJECT ").append(encodedTerm)
                        .append(" FROM ").append(encodedTerm)
                        .append(" TO ").append(encodedTerm)
                        .append(" CC ").append(encodedTerm)
                        .append(" BCC ").append(encodedTerm);
            }
        }
    }

    private void addFlags(StringBuilder builder, Set<Flag> flagSet, boolean addNot) {
        if (flagSet == null || flagSet.isEmpty()) {
            return;
        }

        for (Flag flag : flagSet) {
            if (addNot) {
                builder.append(" NOT");
            }

            //noinspection EnumSwitchStatementWhichMissesCases
            switch (flag) {
                case DELETED: {
                    builder.append(" DELETED");
                    break;
                }
                case SEEN: {
                    builder.append(" SEEN");
                    break;
                }
                case ANSWERED: {
                    builder.append(" ANSWERED");
                    break;
                }
                case FLAGGED: {
                    builder.append(" FLAGGED");
                    break;
                }
                case DRAFT: {
                    builder.append(" DRAFT");
                    break;
                }
                case RECENT: {
                    builder.append(" RECENT");
                    break;
                }
                default: {
                    throw new IllegalStateException("Unsupported flag: " + flag);
                }
            }
        }
    }
}
