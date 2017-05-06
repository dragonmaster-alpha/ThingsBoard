package org.thingsboard.server.dao.sql;

import com.datastax.driver.core.utils.UUIDs;
import org.springframework.data.jpa.domain.Specification;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.model.BaseEntity;
import  static org.thingsboard.server.dao.model.ModelConstants.*;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by Valerii Sosliuk on 5/4/2017.
 */
public abstract class JpaAbstractSearchTimeDao<E extends BaseEntity<D>, D> extends JpaAbstractDao<E, D> {

    protected Specification<E> getTimeSearchPageSpec(TimePageLink pageLink) {
        return new Specification<E>() {
            @Override
            public Predicate toPredicate(Root<E> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder criteriaBuilder) {
                Predicate lowerBound = null;
                Predicate upperBound = null;
                List<Predicate> predicates = new ArrayList<>();
                if (pageLink.isAscOrder()) {
                    if (pageLink.getIdOffset() != null) {
                        lowerBound = criteriaBuilder.greaterThan(root.get(ID_PROPERTY), pageLink.getIdOffset());
                        predicates.add(lowerBound);
                    } else if (pageLink.getStartTime() != null) {
                        UUID startOf = UUIDs.startOf(pageLink.getStartTime());
                        lowerBound = criteriaBuilder.greaterThanOrEqualTo(root.get(ID_PROPERTY), startOf);
                        predicates.add(lowerBound);
                    }
                    if (pageLink.getEndTime() != null) {
                        UUID endOf = UUIDs.endOf(pageLink.getEndTime());
                        upperBound = criteriaBuilder.lessThanOrEqualTo(root.get(ID_PROPERTY), endOf);
                        predicates.add(upperBound);
                    }
                } else {
                    if (pageLink.getIdOffset() != null) {
                        lowerBound = criteriaBuilder.lessThan(root.get(ID_PROPERTY), pageLink.getIdOffset());
                        predicates.add(lowerBound);
                    } else if (pageLink.getEndTime() != null) {
                        UUID endOf = UUIDs.endOf(pageLink.getEndTime());
                        lowerBound = criteriaBuilder.lessThanOrEqualTo(root.get(ID_PROPERTY), endOf);
                        predicates.add(lowerBound);
                    }
                    if (pageLink.getStartTime() != null) {
                        UUID startOf = UUIDs.startOf(pageLink.getStartTime());
                        upperBound = criteriaBuilder.greaterThanOrEqualTo(root.get(ID_PROPERTY), startOf);
                        predicates.add(upperBound);
                    }
                }
                return  criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }
        };
    }
}
