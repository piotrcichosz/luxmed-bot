/**
  * MIT License
  *
  * Copyright (c) 2018 Yevhen Zadyra
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  */
package com.lbs.server.service

import java.time.{LocalTime, ZonedDateTime}

import com.lbs.api.LuxmedApi
import com.lbs.api.json.model._
import org.jasypt.util.text.TextEncryptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import scalaj.http.HttpResponse

@Service
class ApiService extends SessionSupport {

  @Autowired
  protected var dataService: DataService = _
  @Autowired
  private var textEncryptor: TextEncryptor = _

  def getAllCities(accountId: Long): Either[Throwable, List[IdName]] =
    withSession(accountId) { session =>
      LuxmedApi.reservationFilter(session.accessToken, session.tokenType).map(_.cities)
    }

  def getAllClinics(accountId: Long, cityId: Long): Either[Throwable, List[IdName]] =
    withSession(accountId) { session =>
      LuxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId)).map(_.clinics)
    }

  def getAllServices(accountId: Long, cityId: Long, clinicId: Option[Long]): Either[Throwable, List[IdName]] =
    withSession(accountId) { session =>
      LuxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId),
        clinicId = clinicId).map(_.services)
    }

  def getAllDoctors(accountId: Long, cityId: Long, clinicId: Option[Long], serviceId: Long): Either[Throwable, List[IdName]] =
    withSession(accountId) { session =>
      LuxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId),
        clinicId = clinicId, serviceId = Some(serviceId)).map(_.doctors)
    }

  def getDefaultPayer(accountId: Long, cityId: Long, clinicId: Option[Long], serviceId: Long): Either[Throwable, Option[IdName]] =
    withSession(accountId) { session =>
      LuxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId),
        clinicId = clinicId, serviceId = Some(serviceId)).map(_.defaultPayer)
    }

  def getAvailableTerms(accountId: Long, cityId: Long, clinicId: Option[Long], serviceId: Long, doctorId: Option[Long],
                        fromDate: ZonedDateTime = ZonedDateTime.now(), toDate: Option[ZonedDateTime] = None, timeFrom: LocalTime, timeTo: LocalTime,
                        languageId: Long = 10, findFirstFreeTerm: Boolean = false): Either[Throwable, List[AvailableVisitsTermPresentation]] =
    getDefaultPayer(accountId, cityId, clinicId, serviceId).flatMap {
      case Some(payerId) =>
        withSession(accountId) { session =>
          val termsEither = LuxmedApi.availableTerms(session.accessToken, session.tokenType, payerId.id, cityId, clinicId, serviceId, doctorId,
            fromDate, toDate, languageId = languageId, findFirstFreeTerm = findFirstFreeTerm).map(_.availableVisitsTermPresentation)
          termsEither.map { terms =>
            terms.filter { term =>
              val time = term.visitDate.startDateTime.toLocalTime
              time == timeFrom || time == timeTo || (time.isAfter(timeFrom) && time.isBefore(timeTo))
            }
          }
        }
      case None => sys.error(s"Can't determine payer id by user: $accountId, city: $cityId, clinic: $clinicId, service: $serviceId")
    }


  def temporaryReservation(accountId: Long, temporaryReservationRequest: TemporaryReservationRequest, valuationsRequest: ValuationsRequest): Either[Throwable, (TemporaryReservationResponse, ValuationsResponse)] =
    withSession(accountId) { session =>
      LuxmedApi.temporaryReservation(session.accessToken, session.tokenType, temporaryReservationRequest) match {
        case Left(ex) => Left(ex)
        case Right(temporaryReservation) =>
          LuxmedApi.valuations(session.accessToken, session.tokenType, valuationsRequest) match {
            case Left(ex) => Left(ex)
            case Right(valuationsResponse) => Right(temporaryReservation -> valuationsResponse)
          }
      }
    }

  def deleteTemporaryReservation(accountId: Long, temporaryReservationId: Long): Either[Throwable, HttpResponse[String]] =
    withSession(accountId) { session =>
      LuxmedApi.deleteTemporaryReservation(session.accessToken, session.tokenType, temporaryReservationId)
    }

  def reservation(accountId: Long, reservationRequest: ReservationRequest): Either[Throwable, ReservationResponse] =
    withSession(accountId) { session =>
      LuxmedApi.reservation(session.accessToken, session.tokenType, reservationRequest)
    }

  def visitsHistory(accountId: Long, fromDate: ZonedDateTime = ZonedDateTime.now().minusYears(1),
                    toDate: ZonedDateTime = ZonedDateTime.now(), page: Int = 1, pageSize: Int = 100): Either[Throwable, List[HistoricVisit]] =
    withSession(accountId) { session =>
      LuxmedApi.visitsHistory(session.accessToken, session.tokenType, fromDate, toDate, page, pageSize).map(_.historicVisits)
    }

  def reservedVisits(accountId: Long, fromDate: ZonedDateTime = ZonedDateTime.now(),
                     toDate: ZonedDateTime = ZonedDateTime.now().plusMonths(3)): Either[Throwable, List[ReservedVisit]] =
    withSession(accountId) { session =>
      LuxmedApi.reservedVisits(session.accessToken, session.tokenType, fromDate, toDate).map(_.reservedVisits)
    }

  def deleteReservation(accountId: Long, reservationId: Long): Either[Throwable, HttpResponse[String]] =
    withSession(accountId) { session =>
      LuxmedApi.deleteReservation(session.accessToken, session.tokenType, reservationId)
    }

  def login(username: String, password: String): Either[Throwable, LoginResponse] = {
    LuxmedApi.login(username, textEncryptor.decrypt(password))
  }

}
