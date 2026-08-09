package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.CorrectionCandidate;

import java.util.List;

public interface VocabularyCorrectionService {

    List<CorrectionCandidate> correct(List<String> tokens);
}
