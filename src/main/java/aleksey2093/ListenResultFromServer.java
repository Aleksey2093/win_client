package aleksey2093;

import gui.MainFormController;
import gui.ResultsFormController;
import hackIntoSN.GetSomePrivateData;
import hackIntoSN.PersonInfo;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Класс постоянно прослушивает сообщения с сервера, что поймать сообщение о входящем результате подписчика.
 */
public class ListenResultFromServer {
    /**
     * Статическая переменная потока в котором работают методы текущего класса
     */
    private static Thread thread;
    /**
     * Указатель на mainFormController для обновления информации в списке подписок основного окна
     */
    private MainFormController mainFormController;

    /**
     * Запуск методов класса в отдельном потоке
     * @param mainFormController указатель на класс основного окна
     */
    public void startListenThread(MainFormController mainFormController) {
        this.mainFormController = mainFormController;
        thread = new Thread(() -> {
            int err = 0;
            while (true) {
                listenServer();
                try {
                    Thread.sleep(1000);
                } catch (Exception ex) {
                    err++;
                    System.out.println("Ошибка прослушки " + err + ": " + ex.getMessage());
                }
            }
        });
        thread.setName("Прослушка сервера");
        thread.start();
        thread.isInterrupted();
    }

    /**
     * Остановка потока текущего класса
     */
    public void stopListenThread() {
        thread.stop();
    }

    /**
     * Ожидание входящих подключений
     */
    private void listenServer() {
        GiveMeSettings giveMeSettings = new GiveMeSettings();
        ServerSocket serversocket = getServerSocket(giveMeSettings);
        if (serversocket == null)
            return;
        while (true) {
            try {
                Socket socket = serversocket.accept();
                startSocketNewAccept(socket);
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    serversocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                return;
            }
        }
    }

    /**
     * Создание сокета типа сервер для ожидания входящих подключений
     * @param giveMeSettings указатель на класс настроек
     * @return сокет
     */
    private ServerSocket getServerSocket(GiveMeSettings giveMeSettings) {
        int err = 0;
        while (true) {
            try {
                return new ServerSocket(giveMeSettings.getServerPort(3));
            } catch (Exception ex) {
                System.out.println("Не удалось создать сокет для прослушивания ответов с сервера. Ошибка: " +
                        ex.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                err++;
                if (err > 9) {
                    System.out.println("Количество попыток подключения больше 9. " +
                            "Проверьте настройки приложения.");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }
        }
    }

    /**
     * Получение входящего сообщения
     * @param socket указатель на сокет
     */
    private void startSocketNewAccept(Socket socket) {
        try {
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            int len = 0, err = 0;
            byte[] msg = new byte[0];
            while (len == 0) { //запускаем прослушку
                msg = new byte[inputStream.available()];
                len = inputStream.read(msg);
                err++;
                if (err > 100000)
                    return;
                Thread.sleep(500);
            }
            msgPostsProcessing(msg, len);
        } catch (Exception ex) {
            System.out.println("Ошибка в классе прослушки в методке startSocketNewAccept: " + ex.toString());
        }
    }

    /**
     * Вызов диалоговых окно
     * @param what номер диалогового окна
     * @param login имя подписчика
     * @param msg входящее сообщения с сервера
     * @param len длинна сообщения
     */
    private void getResDialogWindow(final int what, final String login, final byte[] msg, final int len) {
        Platform.runLater(() -> {
            if (what == 1) {
                System.out.println("Посмотреть результат пользователя - " + login + "? (yes/no)");
                final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Пришел результат");
                alert.setHeaderText("У пользователя " + login + " новый результат");
                alert.setContentText("Хотите посмотреть на результат '" + login + "'?");
                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == ButtonType.OK) {
                    System.out.println("Пользователь согласился посмотреть результат от " + login);
                    formationListLinks(msg,len,login);
                } //else
                //return false;
            } else if (what == 2) {
                System.out.println("Результат пользователя пуст");
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Информация");
                alert.setHeaderText("");
                alert.setContentText("Результат " + login + " пуст");
                alert.showAndWait();
                //return true;
            } else if (what == 3) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Информация");
                alert.setHeaderText("Ошибка входа");
                alert.setContentText("Неправильный логин или пароль");
                alert.showAndWait();
                //return true;
            }
        });
    }

    /**
     * Обработка входящего сообщения. Проверка на ошибки и передача на извлечение массива ссылок
     * @param msg входящее сообщние
     * @param len длинна сообщения
     */
    private void msgPostsProcessing(byte[] msg, int len)
    {
        //дешифруем
        if (msg[1] == (byte)102) {
            System.out.println("Ошибка в сообщении. Тип: " + msg[1]);
            getResDialogWindow(3,null,null,-1);
        } else if (msg[1] != 2) {
            System.out.println("Получили левое сообщение. Продолжаем прослушку.");
        } else if (msg[2] < 1) {
            System.out.println("Сообщение неверно дешефровано или отправлено. " +
                    "Длинна логина указана как отрицательная. Продолжаем прослушку.");
        } else {
            try {
                String login = new String(msg, 3, msg[2], "UTF-8");
                /*if (!getResDialogWindow(1,login))
                    return;
                //formationListLinks(msg,len,login);*/
                //getResDialogWindow(1,login,msg,len); //окно уведомления о входящем результата
                mainFormController.changeRadioButton(login);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Формирование списка ссылок
     * @param msg входящее сообщение
     * @param len длинна сообщения
     * @param friend имя подписчика
     */
    private void formationListLinks(byte[] msg, int len, String friend) {
        //Мы получили от пользователя разрешение посмотреть на результат запрос от пользователя
        int jb = 3 + msg[2];
                /*и так мы получили список ссылок в виде (4 байта длинна, ссылка, 4 байта длинна, ссылка....).
                * Начинаем его обрабатывать и потом передать в систему выдачи */
        if (jb >= len) {
            getResDialogWindow(2,null,null,-1);
            return;
        }
        ArrayList<String> links = new ArrayList<>();
        while (jb < len) {
            int size = ByteBuffer.wrap(msg, jb, 4).getInt();
            jb += 4;
            try {
                String link = new String(msg, jb, size, "UTF-8");
                link = getIdFromLink(link);
                if (link != null && link.length() != 0)
                    links.add(link);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            jb += size;
        }
        GetSomePrivateData getSomePrivateData = new GetSomePrivateData();
        showWindowResult(getSomePrivateData.vkGet(links),friend);
    }

    /**
     * Вызов окна с результатом подписчика
     * @param list Данные из соц. сетей по результату
     * @param friend имя подписчика
     */
    private void showWindowResult(final ArrayList<PersonInfo> list, final String friend) {
        Platform.runLater(new Runnable() {
            public void run() {
                Stage stage = new Stage();
                Parent root = null;
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("resultsForm.fxml"));
                    root = loader.load();
                    ResultsFormController resultsFormController = loader.getController();
                    resultsFormController.setParametr(list);
                    resultsFormController.getScrollPaneResult();
                } catch (IOException | URISyntaxException e) {
                    e.printStackTrace();
                }
                assert root != null;
                Scene scene = new Scene(root, 600, 790);
                stage.setTitle("Результаты поиска для подписки на " + friend);
                stage.setScene(scene);
                stage.show();
            }
        });
    }

    /**
     * Извлечение id пользователя из ссылки
     * @param link ссылка
     * @return id пользователя
     */
    private String getIdFromLink(String link) {
        String tmp = "";
        if (link.toCharArray()[link.length() - 1] == '/')
            tmp = link.substring(0, link.length() - 2);
        int i = link.length() - 1;
        while (i >= 0 && link.toCharArray()[i] != '/'){
            tmp = link.toCharArray()[i] + tmp;
            i--;
        }
        return tmp;
    }
}
