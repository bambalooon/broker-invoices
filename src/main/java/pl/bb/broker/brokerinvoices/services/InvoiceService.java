package pl.bb.broker.brokerinvoices.services;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import pl.bb.broker.brokerdb.broker.entities.*;
import pl.bb.broker.brokerdb.broker.entities.InvoicesEntity;
import pl.bb.broker.brokerdb.broker.entities.ServicesEntity;
import pl.bb.broker.brokerdb.util.*;
import pl.bb.broker.brokerinvoices.ws.debtcollection.*;
import pl.bb.broker.settings.MailSettings;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: BamBalooon
 * Date: 07.06.14
 * Time: 18:58
 * To change this template use File | Settings | File Templates.
 */

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)   //only with this type i can manage transaction manualy and use hibernate!
public class InvoiceService {

    private static final String invoiceTemplate = "Broker-%d-%d";
    private static final String brokerCompany = "Broker Company\n30-600 Kraków, ul. Jasnogórska 15";
    private static final String formOfPayment = "przelew";
    private static final int dueDate = 14; //days
    private static final String offerServiceName = "Udostępnianie oferty na stronie";
    private static final String offerServiceUnit = "dni";
    private static final double offerServiceUnitPrice = 1.00;
    private static final String reservationServiceName = "Rezerwacja noclegu";
    private static final String reservationServiceUnit = "usł";
    private static final double reservationServiceUnitPrice = 2.00;
    private static final int vat = 23;

    private static final String newInvoiceService = "rs/invoices/new";


    @Resource(name = MailSettings.EMAIL_SESSION_JNDI_PATH)
    private Session mailSession;


    @Schedule(hour = "*", minute = "0,10,20,30,40,50", second = "0", persistent = false)
    public void generateInvoices() throws Exception {
        Calendar now = Calendar.getInstance(); //now
//        int month = now.get(Calendar.MONTH)+1; //because zero-based! January = 0
        int month = now.get(Calendar.MINUTE)/10+now.get(Calendar.HOUR)*6; //do testów
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.HOUR, 0);
        //dueDate
        Calendar dueDate = Calendar.getInstance();
        dueDate.setTime(now.getTime());
        dueDate.add(Calendar.DAY_OF_YEAR, InvoiceService.dueDate);
        //firstOfMonth
        Calendar firstOfMonth = Calendar.getInstance();
        firstOfMonth.setTime(now.getTime());
        firstOfMonth.set(Calendar.DAY_OF_MONTH, 1);


        List<CompaniesEntity> companies = BrokerDBInvoiceUtil.FACTORY.getCompanies();

        int invoiceId = 1;
        for(CompaniesEntity company : companies) {

            if(!company.getUser().isActivated()) {
                continue;
            }

            InvoicesEntity invoice = new InvoicesEntity();
            invoice.setId(String.format(InvoiceService.invoiceTemplate, month, invoiceId));
            invoice.setBuyer(company.getCompanyname() + "\n" + company.getAddress());
            invoice.setSeller(InvoiceService.brokerCompany);
            invoice.setDate(new java.sql.Date(now.getTimeInMillis()));
            invoice.setFormOfPayment(InvoiceService.formOfPayment);
            invoice.setDueDate(new java.sql.Date(dueDate.getTimeInMillis()));

            int days = 0;
            int reservations = 0;
            for(OffersEntity offer : company.getOffers()) {
                Date posted = offer.getPosted();
                Date withdraw = offer.getWithdraw();
                if(withdraw==null) {
                    if(firstOfMonth.getTimeInMillis()>=posted.getTime()) { //posted before month started - counting only this month
                        days += (int) ((now.getTimeInMillis()-firstOfMonth.getTimeInMillis())/1000/60/60/24);
                    }
                    else {
                        days += (int) ((now.getTimeInMillis()-posted.getTime())/1000/60/60/24);
                    }
                }
                else if(withdraw.getTime()>firstOfMonth.getTimeInMillis()) {
                    if(firstOfMonth.getTimeInMillis()>=posted.getTime()) { //posted before month started - counting only this month
                        days += (int) ((withdraw.getTime()-firstOfMonth.getTimeInMillis())/1000/60/60/24);
                    }
                    else {
                        days += (int) ((withdraw.getTime()-posted.getTime())/1000/60/60/24);
                    }
                }
                for(ReservationsEntity reservation : offer.getReservations()) {
                    long arrival = reservation.getArrival().getTime();
                    if(arrival>=firstOfMonth.getTimeInMillis() && arrival<now.getTimeInMillis()) {
                        reservations++;
                    }
                }
            }
            List<ServicesEntity> services = new ArrayList<>();
            double offerPrice = 0;
            if(days>0) {
                ServicesEntity service = new ServicesEntity();
                service.setInvoice(invoice);
                service.setName(InvoiceService.offerServiceName);
                service.setQuantity(days);
                service.setUnit(InvoiceService.offerServiceUnit);
                service.setUnitPrice(BigDecimal.valueOf(InvoiceService.offerServiceUnitPrice));
                service.setVat(InvoiceService.vat);
                offerPrice = InvoiceService.offerServiceUnitPrice*days*(1+((double)InvoiceService.vat)/100);
                service.setLinePrice(BigDecimal.valueOf(offerPrice));
                services.add(service);
            }
            double reservationPrice = 0;
            if(reservations>0) {
                ServicesEntity service = new ServicesEntity();
                service.setInvoice(invoice);
                service.setName(InvoiceService.reservationServiceName);
                service.setQuantity(reservations);
                service.setUnit(InvoiceService.reservationServiceUnit);
                service.setUnitPrice(BigDecimal.valueOf(InvoiceService.reservationServiceUnitPrice));
                service.setVat(InvoiceService.vat);
                reservationPrice = InvoiceService.reservationServiceUnitPrice*reservations*(1+((double)InvoiceService.vat)/100);
                service.setLinePrice(BigDecimal.valueOf(reservationPrice));
                services.add(service);
            }

            invoice.setServices(services);
            invoice.setTotal(BigDecimal.valueOf(offerPrice+reservationPrice));


            PaymentsEntity payment = new PaymentsEntity();
            payment.setInvoiceId(invoice.getId());
            payment.setInvoice(invoice);
            payment.setPaid(false);
            payment.setDebtCollection(false);

            invoice.setPayment(payment);


            if(invoice.getTotal().doubleValue()>0.00) {
                BrokerDBInvoiceUtil.FACTORY.saveInvoice(invoice);
                invoiceId++;
                this.sendInvoice(company, invoice);
            }
        }
    }

    protected void sendInvoice(CompaniesEntity company, InvoicesEntity invoice) throws Exception {
        Client client = Client.create();
        if(company.getResources()==null) {
            this.sendOfflineInvoice(company, invoice);
        }
        else {
            WebResource webResource = client.resource(company.getResources()+InvoiceService.newInvoiceService); //company address
            ClientResponse response = webResource.type(MediaType.APPLICATION_XML_TYPE).put(ClientResponse.class, invoice);
            if(response.getStatus()!=201) {
//                String msg = response.getEntity(String.class);
//                throw new Exception("Invoice service error: "+response.getStatus()+"\n"+msg);
                this.sendOfflineInvoice(company, invoice);
            }
        }
    }

    @Schedule(hour = "*", minute = "0,10,20,30,40,50", second = "0", persistent = false)
    public void sendExpiredInvoices() throws Exception {
        List<InvoicesEntity> invoices = BrokerDBInvoiceUtil.FACTORY.getUnpaidInvoices();
        for(InvoicesEntity invoice : invoices) {
            if(invoice.getDueDate().getTime()<new Date().getTime()) {
                ObjectFactory factory = new ObjectFactory();
                Invoice inv = factory.createInvoice();
                inv.setID(factory.createInvoiceID(invoice.getId()));
                inv.setBuyer(factory.createInvoiceBuyer(invoice.getBuyer()));
                inv.setSeller(factory.createInvoiceSeller(invoice.getSeller()));
                GregorianCalendar date = new GregorianCalendar();
                date.setTime(invoice.getDate());
                XMLGregorianCalendar xmlDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(date);
                inv.setDate(xmlDate);
                GregorianCalendar dueDate = new GregorianCalendar();
                dueDate.setTime(invoice.getDueDate());
                XMLGregorianCalendar xmlDueDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(dueDate);
                inv.setDueDate(xmlDueDate);
                inv.setFormOfPayment(factory.createInvoiceFormOfPayment(invoice.getFormOfPayment()));
                inv.setTotal(invoice.getTotal().doubleValue());
                ArrayOfService services = factory.createArrayOfService();
                List<Service> svcs = services.getService();
                for(ServicesEntity s : invoice.getServices()) {
                    Service svc = factory.createService();
                    svc.setID(s.getId());
                    svc.setName(factory.createServiceName(s.getName()));
                    svc.setQuantity(s.getQuantity());
                    svc.setUnit(factory.createServiceUnit(s.getUnit()));
                    svc.setUnitPrice(s.getUnitPrice().doubleValue());
                    svc.setVat(s.getVat());
                    svc.setLinePrice(s.getLinePrice().doubleValue());
                    svcs.add(svc);
                }
                inv.setServices(factory.createArrayOfService(services));

                DebtCollectionService debtCollectionService = new DebtCollectionService();
                IDebtCollectionService debtService = debtCollectionService.getBasicHttpBindingIDebtCollectionService();
                if(debtService.transferInvoice(inv).equals("added")) {
                    invoice.getPayment().setDebtCollection(true);
                    BrokerDBInvoiceUtil.FACTORY.updateInvoice(invoice);
                }

            }
        }
    }

    protected void sendOfflineInvoice(CompaniesEntity company, InvoicesEntity invoice) throws Exception {
        Message message = new MimeMessage(this.getEmailSession());
        message.setFrom(new InternetAddress("invoices@broker.pl"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(company.getEmail()));
        message.setSubject("Invoice #"+invoice.getId());
        message.setText("It's our monthly invoice - enjoy :)");

        final JAXBContext context = JAXBContext.newInstance(InvoicesEntity.class);
        final Marshaller marshaller = context.createMarshaller();
        final StringWriter stringWriter = new StringWriter();

        marshaller.marshal(invoice, stringWriter);

        MimeBodyPart messageBodyPart = new MimeBodyPart();
        Multipart multipart = new MimeMultipart();
        messageBodyPart.setDataHandler(new DataHandler(stringWriter.toString(), "application/octet-stream; charset=UTF-8"));
        messageBodyPart.setFileName("Invoice#" + invoice.getId() + ".xml");
        multipart.addBodyPart(messageBodyPart);

        message.setContent(multipart);

        Transport.send(message);
    }

    protected Session getEmailSession() throws Exception {
        return mailSession;
    }
}
