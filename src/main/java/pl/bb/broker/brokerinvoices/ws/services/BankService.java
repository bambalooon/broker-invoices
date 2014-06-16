package pl.bb.broker.brokerinvoices.ws.services;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import pl.bb.broker.brokerinvoices.ws.messages.*;

/**
 * Created with IntelliJ IDEA.
 * User: BamBalooon
 * Date: 13.06.14
 * Time: 17:14
 * To change this template use File | Settings | File Templates.
 */

@WebService(name = "BankService")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT, use = SOAPBinding.Use.LITERAL)
public interface BankService {

    @WebMethod
    String transferMoney(MoneyTransfer transfer);
}
