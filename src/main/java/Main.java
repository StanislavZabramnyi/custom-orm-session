import org.postgresql.ds.PGSimpleDataSource;
import orm.Person;
import orm.Session;
import orm.SessionFactory;

public class Main {

    public static void main(String[] args) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL("jdbc:postgresql://localhost:5432/postgres");
        dataSource.setUser("postgres");
        dataSource.setPassword("password");

        SessionFactory sessionFactory = new SessionFactory(dataSource);
        Session session = sessionFactory.createSession();

        Person person = session.find(Person.class, 1L);
        System.out.println(person);
        System.out.println("-----------------------------------------------");
        person.setFirstName("Stas");
        session.close();
        System.out.println(session.find(Person.class, 1L));
    }
}
