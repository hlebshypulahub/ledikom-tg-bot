package com.ledikom.utils;

import com.ledikom.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class BotResponses {

    public static String startMessage() {
        return """
                Вас приветствует бот сети аптек *"Ледиком"*!

                Здесь у вас есть уникальная возможность получить максимум полезных и интересных функций, связанных с вашим здоровьем и комфортом. Давайте рассмотрим, что вы уже можете делать и какие потрясающие возможности скоро будут добавлены:


                *Основные функции:*

                1. 🩺 *Медицинская консультация*: _Наш бот готов помочь вам с вопросами о медицине и здоровье. Получите информацию и советы, не покидая чата._

                2. 🗒️ *Заметки для покупок*: _Создавайте список покупок и сохраняйте его прямо здесь. Удобно и быстро._

                3. 💰 *Купоны на скидки*: _Получайте эксклюзивные купоны (доступны только пользователям бота) для скидок на покупок в наших аптеках._

                4. 👫 *Приглашение друзей*: _Приглашайте своих друзей воспользоваться нашим ботом с помощью реферальной ссылки (доступна в меню) и получайте бонусы за каждого нового пользователя._

                5. 🎶 *Музыка для сна*: _Создайте идеальную атмосферу для сна с нашей музыкой._

                6. 🏥 *Информация о наших аптеках*: _Получите доступ к информации о местоположении и рабочем графике наших аптек._

                7. 🎉 *Акции и новости*: _Будьте в курсе актуальных акций и получайте новости в области здоровья._


                *Скоро добавим:*

                1. ⏰ *Напоминания по лекарствам*: _Настройте удобные напоминания о приеме лекарств._

                2. 🏋️ *Утренняя зарядка*: _Начните день с энергии и активности, повторяя движения за нашим тренером._

                3. 🎁 *Карта клиента*: _Участвуйте в программе лояльности и получайте призы за свои покупки._


                А пока что, чтобы отпраздновать ваше присоединение к *"Ледиком"*, активируйте *приветственный купон со скидкой 5%* на любые товары. Это ваш первый шаг к заботе о вашем здоровье с нами.


                _Не забывайте, что весь функционал доступен через меню бота. Добро пожаловать в будущее здоровья и комфорта с ботом "Ледиком"!_ 🌟""";
    }

    public static String couponAcceptMessage(final Coupon coupon, final boolean inAllPharmacies, final int durationInMinutes) {
        StringBuilder sb = new StringBuilder(coupon.getText() + "\n\n");

        appendPharmacies(sb, coupon.getPharmacies().stream().toList(), inAllPharmacies);
        sb.append("\n\n");

        if (coupon.getStartDate() != null && coupon.getEndDate() != null) {
            sb.append("*С ").append(coupon.getStartDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append(" по ").append(coupon.getEndDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append("*\n\n");
        }

        sb.append("*Купон действует в течение ").append(durationInMinutes).append(" минут. Активируйте его при кассе.*");

        return sb.toString();
    }

    public static String referralMessage(final String refLink, final int referralCount, final Coupon ...couponsFirst10Second20Third30) {
        return "Ваша реферальная ссылка:\n\n\n" + "[" + refLink + "](" + refLink + ")" + "\n\n\n*Количество приглашенных вами пользователей:   "
                + referralCount + "*\n\n\nПоделитесь ссылкой с вашими контактами и получайте бонусы:\n\n"
                + "\uD83E\uDD49 10 приглашенных: " + couponsFirst10Second20Third30[0].getName() + "\n"
                + "\uD83E\uDD48 20 приглашенных: " + couponsFirst10Second20Third30[1].getName() + "\n"
                + "\uD83E\uDD47 30 приглашенных: " + couponsFirst10Second20Third30[2].getName();
    }

    public static String couponExpiredMessage() {
        return "Время вашего купона истекло ⌛";
    }

    public static String triggerReceiveNewsMessage(final User user) {
        return "Подписка на рассылку новостей и акций " + (user.getReceiveNews() ? "включена \uD83D\uDD14" : "отключена \uD83D\uDD15");
    }

    public static String listOfCouponsMessage() {
        return "\uD83D\uDCB8 Ваши купоны:";
    }

    public static String noActiveCouponsMessage() {
        return "У вас нет купонов \uD83D\uDC40\n\n"
                + "Дождитесь новой рассылки акций в наших аптеках, а также приглашайте друзей, используя свою реферальную ссылку ⬇";
    }

    public static String initialCouponText(final String couponTextWithBarcode, final long couponDurationInMinutes) {
        return "Времени осталось: *" + UtilityHelper.convertIntToTimeInt(couponDurationInMinutes) + ":00*" +
                "\n\n" +
                couponTextWithBarcode;
    }

    public static String updatedCouponText(final UserCouponRecord userCouponRecord, final long timeLeftInSeconds) {
        return "Времени осталось: *" + UtilityHelper.convertIntToTimeInt(timeLeftInSeconds / 60) + ":" + UtilityHelper.convertIntToTimeInt(timeLeftInSeconds % 60) +
                "*\n\n" +
                userCouponRecord.getText();
    }

    public static String couponButton(final Coupon coupon) {
        return coupon.getName();
    }

    public static String noteAdded() {
        return "Заметка сохранена \uD83D\uDD16\n\n_*Чтобы просмотреть или редактировать, воспользуйтесь меню бота_";
    }

    public static String editNote(final String note) {
        return "Ваша заметка:\n\n" +
                "`" + note + "`\n\n" +
                "_*Чтобы редактировать, скопируйте текст заметки (нажать на текст), вставьте в поле ввода, измените текст и отправьте сообщение._";
    }

    public static String addNote() {
        return "Чтобы добавить заметку, введите сообщение и отправьте ✏";
    }

    public static String musicMenu() {
        return "Выберите стиль музыки \uD83C\uDFBC";
    }

    public static String musicDurationMenu() {
        return """
                Выберите продолжительность

                _*Музыка остановится автоматически, телефон можно заблокировать и отложить_""";
    }

    public static String goodNight() {
        return "Сеть аптек \"Ледиком\" желает вам добрых снов \uD83D\uDE0C";
    }

    public static String chooseYourCity() {
        return "Выберите ваш город";
    }

    public static String cityAdded(final String cityName) {
        return "Ваш город - *" + City.valueOf(cityName).label + "*";
    }

    public static String newCoupon(final Coupon coupon) {
        return coupon.getNews() + "\n\n" + coupon.getText();
    }

    public static String couponIsNotActive() {
        return "Купон неактивен!";
    }

    public static String yourCityCanUpdate(final City city) {
        return "Ваш город" + (city == null ?
                " не указан.\n\nУкажите его, чтобы получать актуальные новости и акции только для вашего города!"
                :
                " - " + city.label + ".\n\nМожете изменить, выбрав в меню ниже.");
    }

    public static String cityAddedAndNewCouponsGot(final String cityName) {
        return "Ваш город - *" + City.valueOf(cityName).label + "*\n\nПроверьте и воспользуйтесь вашими новыми купонами!";
    }

    public static String promotionAccepted() {
        return "Спасибо, что согласились поучаствовать в нашей акции!\n\n*Поспешите - количество ограниченно!*";
    }

    public static String promotionText(final PromotionFromAdmin promotionFromAdmin, final boolean inAllPharmacies) {
        StringBuilder sb = new StringBuilder(promotionFromAdmin.getText()).append("\n\n");
        appendPharmacies(sb, promotionFromAdmin.getPharmacies(), inAllPharmacies);
        sb.append("\n");
        return sb.toString();
    }

    private static void appendPharmacies(final StringBuilder sb, final List<Pharmacy> pharmacies, final boolean inAllPharmacies) {
        if (inAllPharmacies) {
            sb.append("*Действует во всех аптках сети.*");
        } else {
            sb.append("*Действует в аптках:*\n");
            pharmacies.forEach(pharmacy -> {
                sb.append(pharmacy.getName()).append(" - ").append(pharmacy.getCity().label).append(", ").append(pharmacy.getAddress()).append("\n");
            });
        }
    }

    public static String addSpecialDate() {
        return "\uD83D\uDCC6 Вы можете указать вашу особенную дату, в этот день вы получите подарок от сети аптек \"Ледиком\"!\n\n*Введите и отправьте сообщение в следующем цифровом формате:\n\nдень.месяц*";
    }

    public static String yourSpecialDate(final LocalDateTime specialDate) {
        return "Ваша особенная дата: *" + specialDate.format(DateTimeFormatter.ofPattern("dd.MM")) + "*\n\n" + "\uD83C\uDF81 В этот день вас ждет подарок от сети аптек \"Ледиком\"!";
    }

    public static String specialDay() {
        return "✨ В этот особенный день мы хотим пожелать вам бесконечной удачи, крепкого здоровья и безмерного счастья!\n\nВоспользуйтесь вашим подарком \uD83C\uDF81";
    }

    public static String refCoupon(final int referralCount) {
        return "\uD83E\uDEC2 *Вы пригласили уже " + referralCount + " новых пользователей!*\n\nСпасибо, что совершаете покупки у нас и привлекаете к этому ваших друзей!\n\n\uD83C\uDF81 Вы получаете подарок от сети аптек \"Ледиком\"!";
    }

    public static String responseTimeExceeded() {
        return "Время ожидания на ответ вышло.\n\nВ случае необходимости повторите операцию через меню.";
    }

    public static String consultationWiki() {
        return """
                *Как задавать вопросы боту*

                1. *Сформулируйте вопрос четко и кратко:* Постарайтесь выразить свой вопрос ясно и кратко. Это поможет боту лучше понять ваш запрос и дать более точный ответ.

                2. *Соблюдайте ограничение в 300 символов:* Ваш вопрос должен быть коротким и не превышать 300 символов.

                3. *Соблюдайте тему медицины или здоровья:* Бот специализирован на вопросах по медицине и здоровью, поэтому задавайте вопросы, связанные с этой темой.

                *Примеры хороших вопросов:*
                "Какие симптомы гриппа и каковы методы лечения?"
                "Как улучшить сон и бороться с бессонницей?"
                "Что такое антиоксиданты и почему они важны для здоровья?"
                "Как поддерживать здоровое пищеварение и избегать желудочных проблем?"
                "Какое продукты полезны при недостатке железа?"
                "Как справиться с волнением перед выступлением?"

                Соблюдение этих правил поможет вам получить более точные и полезные ответы от бота в рамках темы *медицины и здоровья*.""";
    }

    public static String waitForGptResponse() {
        return "Бот работает на основе сложных алгоритмов и искусственного интеллекта, и обработка вашего запроса может занять некоторое время." +
                " Обычно это занимает *до 30 секунд*. Бот постарается предоставить наилучший и информативный ответ на ваш вопрос.";
    }
}
