package com.sowad.nourish.payment;

import com.sowad.nourish.activitylog.ActivityLogService;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.inventory.OperationRequests;
import com.sowad.nourish.invoice.InvoiceQueries;
import com.sowad.nourish.payment.command.CollectPayment;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasAnyRole('admin','operator')")
public class PaymentServiceImpl implements PaymentService {
  private final PaymentPosting posting;
  private final OperationRequests requests;
  private final InvoiceQueries queries;
  private final CurrentUserProvider users;
  private final ActivityLogService audit;

  public PaymentServiceImpl(
      PaymentPosting posting,
      OperationRequests requests,
      InvoiceQueries queries,
      CurrentUserProvider users,
      ActivityLogService audit) {
    this.posting = posting;
    this.requests = requests;
    this.queries = queries;
    this.users = users;
    this.audit = audit;
  }

  public Map<String, Object> create(String key, CollectPayment c) {
    String hash = requests.hash(c), prior = requests.begin(key, "payment", hash);
    if (prior != null) return queries.payment(prior);
    var actor = users.getCurrentUser();
    String id = posting.post(c, actor.id());
    audit.record(actor, "CREATE", "payment", id, "Invoice payment collected");
    requests.finish(key, "payment", hash, id);
    return queries.payment(id);
  }
}
