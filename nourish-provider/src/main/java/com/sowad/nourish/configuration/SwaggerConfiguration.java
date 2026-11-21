package com.sowad.nourish.configuration;

import io.swagger.v3.core.converter.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.*;
import java.util.*;
import org.springdoc.core.customizers.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class SwaggerConfiguration {
  private static final Map<String, Class<?>> COMMANDS =
      Map.ofEntries(
          Map.entry(
              "CustomerController#create", com.sowad.nourish.contact.command.SaveContact.class),
          Map.entry(
              "CustomerController#update", com.sowad.nourish.contact.command.SaveContact.class),
          Map.entry(
              "CustomerController#rates",
              com.sowad.nourish.customerrate.command.SaveCustomerRates.class),
          Map.entry(
              "PurchaseController#create", com.sowad.nourish.purchase.command.CreatePurchase.class),
          Map.entry("NotebookController#create", com.sowad.nourish.notebook.command.SaveNote.class),
          Map.entry("NotebookController#update", com.sowad.nourish.notebook.command.SaveNote.class),
          Map.entry(
              "SaleQuoteController#quote", com.sowad.nourish.invoice.SaleQuoteService.Quote.class),
          Map.entry(
              "InvoiceController#create", com.sowad.nourish.invoice.command.CreateInvoice.class),
          Map.entry(
              "InvoiceController#payment", com.sowad.nourish.payment.command.CollectPayment.class),
          Map.entry(
              "ReceivingAccountController#create",
              com.sowad.nourish.receivingaccount.command.SaveReceivingAccount.class),
          Map.entry(
              "ReceivingAccountController#update",
              com.sowad.nourish.receivingaccount.command.SaveReceivingAccount.class),
          Map.entry(
              "CatalogController#createUnit", com.sowad.nourish.catalog.command.SaveUnit.class),
          Map.entry("CatalogController#editUnit", com.sowad.nourish.catalog.command.SaveUnit.class),
          Map.entry(
              "CatalogController#createProduct",
              com.sowad.nourish.catalog.command.SaveProduct.class),
          Map.entry(
              "CatalogController#editProduct", com.sowad.nourish.catalog.command.SaveProduct.class),
          Map.entry(
              "CatalogController#addPackage",
              com.sowad.nourish.catalog.command.SavePackaging.class),
          Map.entry(
              "SupplierController#create", com.sowad.nourish.contact.command.SaveContact.class),
          Map.entry(
              "SupplierController#update", com.sowad.nourish.contact.command.SaveContact.class),
          Map.entry(
              "InventoryController#change", com.sowad.nourish.inventory.command.ChangeStock.class),
          Map.entry(
              "ExpenseController#create", com.sowad.nourish.expense.command.SaveExpense.class),
          Map.entry(
              "ExpenseController#update", com.sowad.nourish.expense.command.SaveExpense.class),
          Map.entry(
              "MigrationController#link", com.sowad.nourish.migration.MigrationService.Link.class));

  @Bean
  OpenAPI nourishOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("NOURISH — Sowad Poltri Shop API")
                .version("1.0")
                .description(
                    "Phase 1–8 API. Login through POST /auth/login, copy accessToken into"
                        + " Authorize. Tokens are opaque bearer tokens, not JWT. Try it out"
                        + " executes real operations against this server. Stock/financial POSTs"
                        + " require a fresh Idempotency-Key per intended operation; reuse it only"
                        + " for an identical retry. Role checks remain enforced by the backend."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Opaque token")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  OperationCustomizer nourishOperations() {
    return (operation, handler) -> {
      String controller = handler.getBeanType().getSimpleName();
      String method = handler.getMethod().getName();
      operation.setTags(List.of(controller.replace("Controller", "")));
      if (controller.equals("AuthenticationController") && method.equals("login")
          || controller.equals("HealthController")) operation.setSecurity(List.of());
      boolean hasBody =
          Arrays.stream(handler.getMethod().getParameters())
              .anyMatch(
                  p ->
                      p.isAnnotationPresent(
                          org.springframework.web.bind.annotation.RequestBody.class));
      Class<?> type = COMMANDS.get(controller + "#" + method);
      if (hasBody && type != null)
        operation.setRequestBody(
            new RequestBody()
                .required(true)
                .content(
                    new Content()
                        .addMediaType(
                            "application/json", new MediaType().schema(resolve(type).schema))));
      if (controller.equals("MigrationController") && method.equals("promote")) {
        var schema = new ComposedSchema();
        for (Class<?> c :
            List.of(
                com.sowad.nourish.contact.command.SaveContact.class,
                com.sowad.nourish.catalog.command.SaveProduct.class,
                com.sowad.nourish.catalog.command.SaveUnit.class,
                com.sowad.nourish.receivingaccount.command.SaveReceivingAccount.class))
          schema.addOneOfItem(resolve(c).schema);
        operation.setRequestBody(
            new RequestBody()
                .required(true)
                .description(
                    "Admin only. Choose the command matching the staged collection; see"
                        + " docs/PHASE_08_GUIDE.md. No automatic transaction import.")
                .content(
                    new Content()
                        .addMediaType("application/json", new MediaType().schema(schema))));
      }
      if (operation.getParameters() != null)
        for (var parameter : operation.getParameters())
          if ("Idempotency-Key".equals(parameter.getName())) {
            parameter.setDescription(
                "Required: 8–80 letters, digits, underscore or hyphen. Use a new key per intended"
                    + " operation; reuse the same key and body only when retrying it.");
            parameter.setExample("replace-with-new-uuid-for-each-operation");
          }
      return operation;
    };
  }

  private static ResolvedSchema resolve(Class<?> type) {
    return ModelConverters.getInstance()
        .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
  }

  @Bean
  OpenApiCustomizer nourishCommandSchemas() {
    return api -> {
      for (Class<?> c : COMMANDS.values())
        resolve(c)
            .referencedSchemas
            .forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
    };
  }
}
