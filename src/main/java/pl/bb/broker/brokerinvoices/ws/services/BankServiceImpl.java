package pl.bb.broker.brokerinvoices.ws.services;

import javax.jws.WebService;

import org.hibernate.HibernateException;
import pl.bb.broker.brokerdb.broker.entities.InvoicesEntity;
import pl.bb.broker.brokerdb.util.BrokerDBInvoiceUtil;
import pl.bb.broker.brokerinvoices.ws.messages.*;

/**
 * Created with IntelliJ IDEA.
 * User: BamBalooon
 * Date: 13.06.14
 * Time: 17:19
 * To change this template use File | Settings | File Templates.
 */

@WebService(endpointInterface = "pl.bb.broker.brokerinvoices.ws.services.BankService")
public class BankServiceImpl implements BankService {
    public static final double activationCost = 1.00;

    @Override
    public String transferMoney(MoneyTransfer transfer) {
        if(transfer.getDescription().matches("^Broker\\-[\\d]+\\-[\\d]+$")) {
            InvoicesEntity invoice = BrokerDBInvoiceUtil.FACTORY.getInvoice(transfer.getDescription());
            if(invoice==null) {
                return "wrong id";
            }
            if(invoice.getTotal().doubleValue()<=transfer.getValue()) {
                invoice.getPayment().setPaid(true);
                BrokerDBInvoiceUtil.FACTORY.updateInvoice(invoice);
                return "paid!";
            }
            else {
                return "you paid not enough!";
            }
        } else {
            try {
                if(transfer.getValue()>=BankServiceImpl.activationCost) {
                    BrokerDBInvoiceUtil.FACTORY.activateUser(transfer.getDescription());
                    return "Aktywacja zakończona powodzeniem.";
                } else {
                    return "Aktywacja zakończona niepowodzeniem.";
                }
            } catch (Exception e) {
                return e.getMessage();
            }

        }
    }

}
