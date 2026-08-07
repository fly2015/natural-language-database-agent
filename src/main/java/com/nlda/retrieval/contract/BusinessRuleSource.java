package com.nlda.retrieval.contract;

import com.nlda.retrieval.model.BusinessRule;

import java.util.List;

public interface BusinessRuleSource {

    List<BusinessRule> rules();
}


